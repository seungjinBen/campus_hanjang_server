-- =============================================================================
-- 02. 얼리버드 효과 측정 (유사 A/B)
-- =============================================================================
-- 목적: 사전등록(얼리버드) +1 선택 보너스 유저 vs 일반 유저의 활동량 비교.
--
-- 한계 (자소서에 반드시 명시):
--   - 사전등록은 자발적 참여 → 자기선택편향(self-selection bias) 존재.
--     "얼리버드가 더 많이 선택했다"는 결과는 "보너스 때문"일 수도, "원래 관심 큰
--     유저가 사전등록을 선택했기 때문"일 수도 있어 인과 분리 불가.
--   - 운영 기간이 짧아 이중차분(DiD) 전후 비교로 보완 불가.
--   → 효과 "크기"는 보류하고 분포 차이만 보고하되 한계를 함께 서술.
--
-- 산출: group별 user_count / 평균·중앙값 selections / 평균 contact_reveals /
--       평균 notes / 평균 active_days / 평균 match_score
-- =============================================================================

WITH user_activity AS (
    SELECT
        u.id,
        u.is_early_bird,
        COUNT(s.id) AS total_selections,
        COUNT(s.id) FILTER (WHERE s.type = 'CONTACT_REVEALED') AS contact_reveals,
        COUNT(s.id) FILTER (WHERE s.type = 'NOTE_SENT') AS notes_sent,
        COUNT(DISTINCT DATE(s.created_at)) AS active_days,
        AVG(s.match_score) AS avg_match_score
    FROM users u
    LEFT JOIN selections s ON s.selector_id = u.id
    WHERE u.is_student_verified = true  -- 얼리버드 요건이 인증 완료이므로 비교군을 통일
    GROUP BY u.id, u.is_early_bird
)
SELECT
    is_early_bird,
    COUNT(*) AS user_count,
    ROUND(AVG(total_selections)::numeric, 2) AS avg_selections,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY total_selections) AS median_selections,
    ROUND(AVG(contact_reveals)::numeric, 2) AS avg_contact_reveals,
    ROUND(AVG(notes_sent)::numeric, 2) AS avg_notes,
    ROUND(AVG(active_days)::numeric, 2) AS avg_active_days,
    ROUND(AVG(avg_match_score)::numeric, 3) AS avg_match_score
FROM user_activity
GROUP BY is_early_bird
ORDER BY is_early_bird DESC;

-- 부록: 분포 자체 비교용 (pandas boxplot/histogram)
--   SELECT u.is_early_bird, COUNT(s.id) AS total_selections
--   FROM users u LEFT JOIN selections s ON s.selector_id = u.id
--   WHERE u.is_student_verified = true
--   GROUP BY u.id, u.is_early_bird;
