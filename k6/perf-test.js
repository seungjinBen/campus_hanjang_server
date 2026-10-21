/**
 * 캠퍼스한장 성능 테스트
 *
 * Stage 3 — 100명 동시 접속: 응답시간 / 에러율 기준선 측정
 * Stage 4 — 500명 동시 접속: 임계점(HikariCP 커넥션 풀) 확인
 *
 * 실행:
 *   k6 run k6/perf-test.js
 *   k6 run --out json=k6/results/raw.json k6/perf-test.js
 *
 * 사전 조건:
 *   - 백엔드 dev 프로파일 실행 중 (localhost:8080)
 *   - PostgreSQL 실행 중
 *   - application-dev.yml rate-limit.local-login-per-hour >= 200 (현재 10000 ✓)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { scenario } from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_USER_COUNT = 100;

// ── 커스텀 메트릭 ──────────────────────────────────────────────────────────
const customErrRate  = new Rate('custom_error_rate');
const statusApiMs    = new Trend('api_status_ms',    true);   // /api/service/status
const profileApiMs   = new Trend('api_profile_ms',   true);   // /api/users/profile-complete
const matchCardsApiMs = new Trend('api_match_cards_ms', true); // /api/match/cards

// ── 테스트 옵션 ────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ─ Stage 3: 100명 ──────────────────────────────────────────────────────
    stage3_100vus: {
      executor:          'ramping-vus',
      exec:              'runTest',
      gracefulRampDown:  '10s',
      stages: [
        { duration: '20s', target: 10  }, // 워밍업
        { duration: '40s', target: 100 }, // 100명 증가
        { duration: '3m',  target: 100 }, // 100명 유지 → 기준선 측정
        { duration: '20s', target: 0   }, // 안정화
      ],
    },
    // ─ Stage 4: 500명 ──────────────────────────────────────────────────────
    stage4_500vus: {
      executor:          'ramping-vus',
      exec:              'runTest',
      startTime:         '5m30s',          // stage3 종료 후 30초 휴식
      gracefulRampDown:  '20s',
      stages: [
        { duration: '1m',  target: 200 }, // 200명 증가
        { duration: '1m',  target: 500 }, // 500명 증가
        { duration: '3m',  target: 500 }, // 500명 유지 → 임계점 확인
        { duration: '30s', target: 0   }, // 종료
      ],
    },
  },

  thresholds: {
    // ── Stage 3 통과 기준 ──────────────────────────────────────────────────
    'http_req_duration{scenario:stage3_100vus}': ['p(95)<800', 'p(99)<2000'],
    'http_req_failed{scenario:stage3_100vus}':   ['rate<0.02'],

    // ── Stage 4 임계점 기준 (실패 = 한계 도달) ─────────────────────────────
    'http_req_duration{scenario:stage4_500vus}': ['p(95)<3000', 'p(99)<10000'],
    'http_req_failed{scenario:stage4_500vus}':   ['rate<0.10'],

    // ── 엔드포인트별 p95 (Stage 3 기준선) ─────────────────────────────────
    'api_status_ms{scenario:stage3_100vus}':     ['p(95)<300'],
    'api_profile_ms{scenario:stage3_100vus}':    ['p(95)<500'],
    'api_match_cards_ms{scenario:stage3_100vus}': ['p(95)<800'],

    // ── 전체 커스텀 에러율 ─────────────────────────────────────────────────
    custom_error_rate: ['rate<0.05'],
  },

  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

// ── Setup: 테스트 유저 100명 생성/로그인 ──────────────────────────────────
export function setup() {
  console.log(`\n[SETUP] 테스트 유저 ${TEST_USER_COUNT}명 준비 중 (local login)...`);

  const tokens = [];

  for (let i = 0; i < TEST_USER_COUNT; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/local/login`,
      JSON.stringify({
        email:    `k6_perf_${String(i).padStart(3, '0')}@test.local`,
        password: 'Perf_Test_1234!',
      }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags:    { phase: 'setup' },
      }
    );

    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        if (body.success && body.data?.accessToken) {
          tokens.push(body.data.accessToken);
        }
      } catch (_) { /* ignore */ }
    } else if (res.status === 429) {
      console.error(
        `[SETUP] Rate limit 초과 (i=${i}). ` +
        `application-dev.yml: rate-limit.local-login-per-hour 값 확인`
      );
      break;
    } else {
      console.warn(`[SETUP] 유저 ${i} 실패: HTTP ${res.status}`);
    }
  }

  if (tokens.length < 10) {
    throw new Error(
      `[SETUP] 토큰 ${tokens.length}개 확보 (최소 10 필요). ` +
      `백엔드가 localhost:8080 에서 실행 중인지 확인하세요.`
    );
  }

  console.log(`[SETUP] 완료 — 토큰 ${tokens.length}/${TEST_USER_COUNT}개 확보\n`);
  return { tokens };
}

// ── 테스트 시나리오 (Stage 3 / Stage 4 공통) ─────────────────────────────
export function runTest(data) {
  const tokens    = data.tokens;
  const token     = tokens[(__VU - 1) % tokens.length];
  const stageName = scenario.name; // 'stage3_100vus' | 'stage4_500vus'

  const authOpts = {
    headers: {
      'Content-Type':  'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  // ── 1. 서비스 상태 API (인증 불필요 · DB 미조회) ─────────────────────────
  group('1_service_status', () => {
    const res = http.get(`${BASE_URL}/api/service/status`);
    statusApiMs.add(res.timings.duration, { scenario: stageName });

    const pass = check(res, {
      '[status] 200': (r) => r.status === 200,
      '[status] matchingEnabled 포함': (r) => {
        try { return typeof JSON.parse(r.body).data?.matchingEnabled === 'boolean'; }
        catch { return false; }
      },
    });
    customErrRate.add(pass ? 0 : 1);
  });

  sleep(0.3);

  // ── 2. 프로필 완성도 (DB 조회 · JWT 검증 포함) ───────────────────────────
  group('2_profile_complete', () => {
    const res = http.get(`${BASE_URL}/api/users/profile-complete`, authOpts);
    profileApiMs.add(res.timings.duration, { scenario: stageName });

    const pass = check(res, {
      '[profile] 200': (r) => r.status === 200,
      '[profile] complete 필드': (r) => {
        try { return typeof JSON.parse(r.body).data?.complete === 'boolean'; }
        catch { return false; }
      },
    });
    customErrRate.add(pass ? 0 : 1);
  });

  sleep(0.3);

  // ── 3. 매칭 카드 (AOP 피처 토글 · DB 조회) ───────────────────────────────
  //      5/6 이전 → 403 MATCHING_NOT_AVAILABLE (정상 동작)
  group('3_match_cards', () => {
    const res = http.get(`${BASE_URL}/api/match/cards`, authOpts);
    matchCardsApiMs.add(res.timings.duration, { scenario: stageName });

    const pass = check(res, {
      '[cards] 200 또는 403': (r) => r.status === 200 || r.status === 403,
      '[cards] 500 없음':     (r) => r.status !== 500,
    });
    customErrRate.add(pass ? 0 : 1);
  });

  sleep(0.5);
}

// ── 결과 요약 출력 ─────────────────────────────────────────────────────────
export function handleSummary(data) {
  const m = data.metrics;

  const ms  = (key, stat) => {
    const v = m[key]?.values?.[stat];
    return v != null ? `${v.toFixed(0).padStart(6)}ms` : '   N/A  ';
  };
  const pct = (key, stat) => {
    const v = m[key]?.values?.[stat];
    return v != null ? `${(v * 100).toFixed(2).padStart(6)}%` : '   N/A  ';
  };
  const cnt = (key) => {
    const v = m[key]?.values?.count;
    return v != null ? String(v).padStart(7) : '    N/A';
  };

  // 실패한 임계값 수집
  const failed = [];
  if (data.thresholds) {
    for (const [name, info] of Object.entries(data.thresholds)) {
      if (!info.ok) failed.push(`  ✗  ${name}`);
    }
  }

  const s3d = 'http_req_duration{scenario:stage3_100vus}';
  const s4d = 'http_req_duration{scenario:stage4_500vus}';
  const s3f = 'http_req_failed{scenario:stage3_100vus}';
  const s4f = 'http_req_failed{scenario:stage4_500vus}';

  const report = `

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  캠퍼스한장 성능 테스트 결과
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ┌────────────────────────────────────────────────────────────┐
  │  Stage 3 — 최대 100명 (기준선)              요청: ${cnt(s3d)}건 │
  ├────────────┬────────────┬────────────┬───────────────────┤
  │  p50       │  p95       │  p99       │  에러율            │
  │  ${ms(s3d, 'p(50)')}  │  ${ms(s3d, 'p(95)')}  │  ${ms(s3d, 'p(99)')}  │  ${pct(s3f, 'rate')}            │
  └────────────┴────────────┴────────────┴───────────────────┘

  ┌────────────────────────────────────────────────────────────┐
  │  Stage 4 — 최대 500명 (임계점)              요청: ${cnt(s4d)}건 │
  ├────────────┬────────────┬────────────┬───────────────────┤
  │  p50       │  p95       │  p99       │  에러율            │
  │  ${ms(s4d, 'p(50)')}  │  ${ms(s4d, 'p(95)')}  │  ${ms(s4d, 'p(99)')}  │  ${pct(s4f, 'rate')}            │
  └────────────┴────────────┴────────────┴───────────────────┘

  ── 엔드포인트별 p95 (Stage 3 기준) ────────────────────────────
  /api/service/status          ${ms('api_status_ms',     'p(95)')}  목표 <300ms
  /api/users/profile-complete  ${ms('api_profile_ms',    'p(95)')}  목표 <500ms
  /api/match/cards             ${ms('api_match_cards_ms','p(95)')}  목표 <800ms

  ── 임계값 결과 ────────────────────────────────────────────────
${failed.length === 0
    ? '  ✓  모든 임계값 통과'
    : failed.join('\n')}

  ── HikariCP 병목 판독표 ───────────────────────────────────────
  Stage 4  p99 < 5s    → 정상  (커넥션 풀 여유)
  Stage 4  p99 5~20s   → 경고  (커넥션 대기 발생 — pool size 증설 검토)
  Stage 4  p99 > 20s   → 위험  (connectionTimeout 초과 → 500 에러)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;

  return {
    stdout:                   report,
    'k6/results/summary.json': JSON.stringify(data, null, 2),
  };
}
