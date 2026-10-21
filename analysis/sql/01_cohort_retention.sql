-- =============================================================================
-- 01. 가입 코호트 리텐션
-- =============================================================================
-- 목적: 가입 날짜별 코호트의 N일차 활동(selection 발생) 유저 비율을 집계.
--       "가입만 하고 사라지는 유저"와 "꾸준히 돌아오는 유저"를 코호트 단위로 구분.
--
-- 산출 컬럼: signup_date, cohort_size, day_offset, active_users, retention_pct
--           → pandas pivot_table로 heatmap 시각화
--
-- 민감 정보 처리: user_id는 집계에만 사용, nickname/kakao_id/contact_* 노출 없음.
-- =============================================================================

WITH signup_cohort AS (
    -- 탈퇴(is_active=false) 유저도 활동 이력은 유효하므로 전체 포함
    SELECT
        id AS user_id,
        DATE(created_at) AS signup_date
    FROM users
),
daily_activity AS (
    -- 하루에 여러 번 선택해도 "활동일 1개"로 집계
    SELECT DISTINCT
        selector_id AS user_id,
        DATE(created_at) AS activity_date
    FROM selections
),
cohort_size AS (
    SELECT signup_date, COUNT(*) AS cohort_size
    FROM signup_cohort
    GROUP BY signup_date
),
cohort_activity AS (
    SELECT
        c.signup_date,
        (a.activity_date - c.signup_date)::int AS day_offset,
        COUNT(DISTINCT c.user_id) AS active_users
    FROM signup_cohort c
    JOIN daily_activity a
        ON a.user_id = c.user_id
       AND a.activity_date >= c.signup_date
    GROUP BY c.signup_date, (a.activity_date - c.signup_date)
)
SELECT
    cs.signup_date,
    cs.cohort_size,
    ca.day_offset,
    COALESCE(ca.active_users, 0) AS active_users,
    ROUND(COALESCE(ca.active_users, 0)::numeric / NULLIF(cs.cohort_size, 0) * 100, 1) AS retention_pct
FROM cohort_size cs
LEFT JOIN cohort_activity ca ON ca.signup_date = cs.signup_date
WHERE ca.day_offset IS NULL OR ca.day_offset BETWEEN 0 AND 14
ORDER BY cs.signup_date, ca.day_offset NULLS FIRST;
