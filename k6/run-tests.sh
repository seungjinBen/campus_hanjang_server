#!/usr/bin/env bash
# ============================================================
# CampusHanjang k6 부하 테스트 실행 스크립트
# 사용법: bash k6/run-tests.sh [baseline|optimized|compare]
# ============================================================

set -e

RESULTS_DIR="k6/results"
mkdir -p "$RESULTS_DIR"

check_server() {
    echo "서버 상태 확인 중..."
    if ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "❌ 서버가 실행되지 않았습니다."
        echo "   ./gradlew bootRun 을 먼저 실행하세요."
        exit 1
    fi
    echo "✅ 서버 정상"
}

seed_data() {
    echo ""
    echo "테스트 데이터 시드 실행..."
    psql -U bsjworld -d campus -f k6/seed-data.sql
    echo "✅ 시드 완료"
}

run_baseline() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " STEP 1: Baseline 측정 (N+1 개선 전)"
    echo "══════════════════════════════════════════"
    k6 run \
        --out json="$RESULTS_DIR/baseline.json" \
        --summary-export="$RESULTS_DIR/baseline-summary.json" \
        k6/load-test.js 2>&1 | tee "$RESULTS_DIR/baseline.log"
    echo ""
    echo "✅ Baseline 결과 저장: $RESULTS_DIR/baseline.log"
}

run_optimized() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " STEP 2: Optimized 측정 (N+1 개선 후)"
    echo "══════════════════════════════════════════"
    k6 run \
        --out json="$RESULTS_DIR/optimized.json" \
        --summary-export="$RESULTS_DIR/optimized-summary.json" \
        k6/load-test.js 2>&1 | tee "$RESULTS_DIR/optimized.log"
    echo ""
    echo "✅ Optimized 결과 저장: $RESULTS_DIR/optimized.log"
}

compare_results() {
    echo ""
    echo "══════════════════════════════════════════"
    echo " STEP 3: 결과 비교"
    echo "══════════════════════════════════════════"

    if [[ ! -f "$RESULTS_DIR/baseline-summary.json" ]] || [[ ! -f "$RESULTS_DIR/optimized-summary.json" ]]; then
        echo "❌ 비교 대상 파일이 없습니다. baseline과 optimized를 모두 먼저 실행하세요."
        exit 1
    fi

    echo ""
    echo "=== GET /api/match/cards ==="
    echo "[ Baseline ]"
    jq '.metrics["http_req_duration_cards"].values | {avg: (.avg|round), "p(90)": (."p(90)"|round), "p(95)": (."p(95)"|round), "p(99)": (."p(99)"|round)}' \
        "$RESULTS_DIR/baseline-summary.json" 2>/dev/null || grep -A5 "cards" "$RESULTS_DIR/baseline.log" | head -5

    echo "[ Optimized ]"
    jq '.metrics["http_req_duration_cards"].values | {avg: (.avg|round), "p(90)": (."p(90)"|round), "p(95)": (."p(95)"|round), "p(99)": (."p(99)"|round)}' \
        "$RESULTS_DIR/optimized-summary.json" 2>/dev/null || grep -A5 "cards" "$RESULTS_DIR/optimized.log" | head -5

    echo ""
    echo "상세 비교는 아래 결과 파일을 확인하세요:"
    echo "  Baseline  : $RESULTS_DIR/baseline.log"
    echo "  Optimized : $RESULTS_DIR/optimized.log"
}

case "${1:-all}" in
    baseline)
        check_server
        run_baseline
        ;;
    optimized)
        check_server
        run_optimized
        ;;
    compare)
        compare_results
        ;;
    seed)
        seed_data
        ;;
    all)
        check_server
        seed_data
        run_baseline
        echo ""
        echo "⚠️  N+1 수정이 적용된 상태인지 확인 후 optimized 테스트를 진행하세요."
        echo "   bash k6/run-tests.sh optimized"
        echo "   bash k6/run-tests.sh compare"
        ;;
    *)
        echo "사용법: $0 [baseline|optimized|compare|seed|all]"
        exit 1
        ;;
esac
