-- =============================================================================
-- 08. 가을 시즌 분모 통일 재집계 (운영 종료 시점 스냅샷)
-- =============================================================================
-- 목적: 포폴/자소서의 가을 시즌 지표를 "같은 집합에서 분자·분모가 나오도록" 재집계.
--       기존에 '343명(최대 사용자)'과 '274건(임의 시점 인증 레코드)'이 다른 시점의
--       스냅샷이어서 비율이 성립하지 않던 문제를 운영 종료 시점으로 통일해 해소.
--
-- 분모 통일 원칙:
--   - 분모: 가을 범위의 users 가입자 수 (유니크 유저)
--   - 분자: 같은 유저들의 student_verifications 최종 상태 (유저별 가장 최근 레코드)
--   - 가을 범위: 2026-09-21(사전등록 시작) ~ 2026-10-02(마지막 날)
--     ※ 범위가 다르면 아래 날짜만 수정
-- =============================================================================

-- (A) 가을 시즌 가입자 수 (분모 확정)
SELECT COUNT(*) AS fall_signups
FROM users
WHERE created_at >= '2026-09-21'
  AND created_at <  '2026-10-03';

-- (B) 가을 가입자 중 "유저별 최종 인증 상태" 분포 (분자 확정)
--     한 유저가 재촬영 후 재시도해 자동승인된 경우 → 최종 상태 = AUTO_APPROVED
WITH fall_users AS (
    SELECT id
    FROM users
    WHERE created_at >= '2026-09-21'
      AND created_at <  '2026-10-03'
),
latest_verification AS (
    SELECT DISTINCT ON (sv.user_id)
        sv.user_id,
        sv.status,
        sv.verification_method,
        sv.confidence_score,
        sv.processing_ms,
        sv.llm_calls,
        sv.created_at
    FROM student_verifications sv
    JOIN fall_users fu ON fu.id = sv.user_id
    ORDER BY sv.user_id, sv.created_at DESC
)
SELECT
    status,
    COUNT(*) AS unique_users,
    ROUND(COUNT(*)::numeric
          / (SELECT COUNT(*) FROM fall_users) * 100, 1) AS pct_of_signups
FROM latest_verification
GROUP BY status
UNION ALL
-- 가입했지만 인증 시도조차 안 한 유저 (NULL 분기)
SELECT
    'NO_ATTEMPT' AS status,
    (SELECT COUNT(*) FROM fall_users)
        - (SELECT COUNT(*) FROM latest_verification) AS unique_users,
    ROUND(((SELECT COUNT(*) FROM fall_users)
           - (SELECT COUNT(*) FROM latest_verification))::numeric
          / (SELECT COUNT(*) FROM fall_users) * 100, 1) AS pct_of_signups
ORDER BY status;

-- (C) 자동승인 유저의 평균 confidence / 처리시간 / 턴수
WITH fall_users AS (
    SELECT id FROM users
    WHERE created_at >= '2026-09-21' AND created_at < '2026-10-03'
),
latest_verification AS (
    SELECT DISTINCT ON (sv.user_id)
        sv.user_id, sv.status, sv.verification_method,
        sv.confidence_score, sv.processing_ms, sv.llm_calls
    FROM student_verifications sv
    JOIN fall_users fu ON fu.id = sv.user_id
    ORDER BY sv.user_id, sv.created_at DESC
)
SELECT
    verification_method,
    COUNT(*) AS auto_approved_users,
    ROUND(AVG(confidence_score)::numeric, 3) AS avg_confidence,
    ROUND(AVG(processing_ms)::numeric, 0) AS avg_processing_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY processing_ms) AS p95_processing_ms,
    ROUND(AVG(llm_calls)::numeric, 2) AS avg_llm_calls
FROM latest_verification
WHERE status = 'AUTO_APPROVED'
GROUP BY verification_method
UNION ALL
SELECT 'TOTAL', COUNT(*),
    ROUND(AVG(confidence_score)::numeric, 3),
    ROUND(AVG(processing_ms)::numeric, 0),
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY processing_ms),
    ROUND(AVG(llm_calls)::numeric, 2)
FROM latest_verification
WHERE status = 'AUTO_APPROVED';
