/**
 * CampusHanjang k6 부하 테스트
 *
 * 측정 목표: N+1 쿼리 개선 전후 응답 시간 비교 (포트폴리오용)
 *
 * 사전 준비:
 *   1. psql -U bsjworld -d campus -f k6/seed-data.sql
 *   2. ./gradlew bootRun (dev 프로파일)
 *   3. k6 run k6/load-test.js --out json=k6/results/baseline.json
 *      (N+1 수정 후) k6 run k6/load-test.js --out json=k6/results/optimized.json
 *
 * 테스트 엔드포인트:
 *   GET  /api/match/cards          ← N+1 핵심: 10장 × (사진+특징) = 22→4쿼리
 *   POST /api/match/select         ← 비관적 락 성능
 *   GET  /api/match/received/notes ← N+1: 쪽지 N건 × (사진+특징) = 2N+1→3쿼리
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── 커스텀 지표 ──────────────────────────────────────────────────────────────
const cardsLatency  = new Trend('http_req_duration_cards',  true);  // true = ms 단위
const selectLatency = new Trend('http_req_duration_select', true);
const notesLatency  = new Trend('http_req_duration_notes',  true);
const rateLimited   = new Counter('rate_limited_429');
const appErrors     = new Counter('app_errors_5xx');
const errorRate     = new Rate('error_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ─── 부하 단계 ────────────────────────────────────────────────────────────────
export const options = {
    stages: [
        { duration: '30s', target: 50   },  // Stage 1: 실제 피크 수준
        { duration: '60s', target: 500  },  // Stage 2: 10배 스트레스
        { duration: '30s', target: 1000 },  // Stage 3: 한계 스파이크
        { duration: '30s', target: 0    },  // Stage 4: 회복
    ],
    thresholds: {
        // N+1 개선 후에는 p95 < 500ms 목표
        'http_req_duration_cards':  ['p(95)<2000'],
        'http_req_duration_select': ['p(95)<3000'],
        'http_req_duration_notes':  ['p(95)<2000'],
        'error_rate':               ['rate<0.05'],
    },
    summaryTrendStats: ['min', 'avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

// ─── 토큰 풀 로드 (setup: VU 시작 전 1회 실행) ───────────────────────────────
export function setup() {
    const res = http.get(`${BASE_URL}/api/dev/test-tokens?gender=MALE&limit=500`);

    if (res.status !== 200) {
        throw new Error(`토큰 발급 실패 status=${res.status} body=${res.body.slice(0, 200)}`);
    }

    const body = JSON.parse(res.body);
    const users = body.data;

    if (!users || users.length === 0) {
        throw new Error('테스트 유저 없음 — k6/seed-data.sql 먼저 실행 필요');
    }

    console.log(`✅ 테스트 유저 로드 완료: ${users.length}명`);
    return { users };
}

// ─── VU 메인 루프 ─────────────────────────────────────────────────────────────
export default function (data) {
    const { users } = data;

    // VU별 고유 유저 배정 (1000 VU > 500 유저이면 순환)
    const user = users[(__VU - 1) % users.length];
    const headers = {
        'Authorization': `Bearer ${user.token}`,
        'Content-Type': 'application/json',
    };

    // ── Step 1: 매칭 카드 조회 ─────────────────────────────────────────────────
    // N+1 핵심 구간: 카드 10장 × (userTraits 쿼리 + userPhotos 쿼리) = 22쿼리
    const cardsRes = http.get(`${BASE_URL}/api/match/cards`, { headers, tags: { endpoint: 'cards' } });

    if (cardsRes.status === 429) {
        rateLimited.add(1);
    } else if (cardsRes.status >= 500) {
        appErrors.add(1);
        errorRate.add(1);
    } else if (cardsRes.status === 200) {
        cardsLatency.add(cardsRes.timings.duration);
        errorRate.add(0);

        check(cardsRes, {
            'cards: success': (r) => r.status === 200,
            'cards: has data': (r) => {
                try { return JSON.parse(r.body).data !== null; } catch { return false; }
            },
        });

        // ── Step 2: 선택하기 ───────────────────────────────────────────────────
        // 비관적 락(SELECT FOR UPDATE) 성능 측정
        let cards = [];
        try {
            cards = JSON.parse(cardsRes.body).data?.cards || [];
        } catch (_) {}

        if (cards.length > 0) {
            const candidateId = cards[0].candidateId;
            const selectRes = http.post(
                `${BASE_URL}/api/match/select`,
                JSON.stringify({ candidateId }),
                { headers, tags: { endpoint: 'select' } }
            );

            if (selectRes.status === 429) {
                rateLimited.add(1);
            } else if (selectRes.status >= 500) {
                appErrors.add(1);
                errorRate.add(1);
            } else if (selectRes.status === 200) {
                selectLatency.add(selectRes.timings.duration);
                check(selectRes, {
                    'select: success': (r) => r.status === 200,
                });
            }
            // 400 (DAILY_SELECT_LIMIT_EXCEEDED) — 하루 3회 초과, 정상 비즈니스 응답이므로 에러 불카운트
        }
    }

    sleep(0.1);

    // ── Step 3: 쪽지 수신함 조회 ──────────────────────────────────────────────
    // 남성 유저는 빈 목록 반환 (여성 유저 100명은 1건씩 보유)
    // N+1 구간: 쪽지 N건 × (userTraits + userPhotos) = 2N+1 → 3쿼리
    const notesRes = http.get(
        `${BASE_URL}/api/match/received/notes`,
        { headers, tags: { endpoint: 'notes' } }
    );

    if (notesRes.status === 429) {
        rateLimited.add(1);
    } else if (notesRes.status >= 500) {
        appErrors.add(1);
        errorRate.add(1);
    } else if (notesRes.status === 200) {
        notesLatency.add(notesRes.timings.duration);
        errorRate.add(0);
        check(notesRes, {
            'notes: success': (r) => r.status === 200,
        });
    }

    sleep(0.1);
}

// ─── 최종 요약 출력 ───────────────────────────────────────────────────────────
export function handleSummary(data) {
    const fmt = (trend, stat) => {
        const val = trend?.[stat];
        return val !== undefined ? val.toFixed(1) + 'ms' : 'N/A';
    };

    const cards  = data.metrics['http_req_duration_cards'];
    const select = data.metrics['http_req_duration_select'];
    const notes  = data.metrics['http_req_duration_notes'];
    const rl     = data.metrics['rate_limited_429'];
    const err    = data.metrics['error_rate'];
    const totalReqs = data.metrics['http_reqs'];

    const summary = `
╔══════════════════════════════════════════════════════════════════╗
║         CampusHanjang k6 부하 테스트 결과 요약                    ║
╠══════════════════════════════════════════════════════════════════╣
║ 총 요청 수  : ${String(totalReqs?.values?.count ?? 'N/A').padEnd(12)} RPS: ${String((totalReqs?.values?.rate ?? 0).toFixed(1)).padEnd(10)}      ║
║ 에러율      : ${String((err?.values?.rate * 100 ?? 0).toFixed(2) + '%').padEnd(12)} 429(Rate Limit): ${String(rl?.values?.count ?? 0).padEnd(6)}      ║
╠══════════════════════════════════════════════════════════════════╣
║  엔드포인트             │  avg     │  p(90)   │  p(95)   │  p(99)  ║
╠══════════════════════════════════════════════════════════════════╣
║  GET /api/match/cards   │ ${fmt(cards?.values, 'avg').padEnd(8)} │ ${fmt(cards?.values, 'p(90)').padEnd(8)} │ ${fmt(cards?.values, 'p(95)').padEnd(8)} │ ${fmt(cards?.values, 'p(99)')} ║
║  POST /api/match/select │ ${fmt(select?.values, 'avg').padEnd(8)} │ ${fmt(select?.values, 'p(90)').padEnd(8)} │ ${fmt(select?.values, 'p(95)').padEnd(8)} │ ${fmt(select?.values, 'p(99)')} ║
║  GET /received/notes    │ ${fmt(notes?.values, 'avg').padEnd(8)} │ ${fmt(notes?.values, 'p(90)').padEnd(8)} │ ${fmt(notes?.values, 'p(95)').padEnd(8)} │ ${fmt(notes?.values, 'p(99)')} ║
╚══════════════════════════════════════════════════════════════════╝
`;

    console.log(summary);

    return {
        stdout: summary,
    };
}
