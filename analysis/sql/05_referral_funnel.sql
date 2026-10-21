-- =============================================================================
-- 05. 리퍼럴 전환 퍼널
-- =============================================================================
-- 목적: 초대 링크로 들어온 유저가 각 단계에서 얼마나 drop-off 하는지 측정.
--       단계: PENDING(가입) → VERIFIED(인증 완료) → REWARDED(보상 확정) → ACTIVE(실제 선택).
--
-- 산출:
--   (A) 전체 퍼널 단계별 count + 전환율
--   (B) 초대자별 보상 적립 분포 상위 10 (하루 1회 상한 정책의 실효성 간접 확인)
--
-- 민감 정보: referrer_id/referee_id는 UUID — 외부 식별 불가하므로 상위 10 조회만 포함.
--           공개 포트폴리오에 올릴 때는 referrer_id 열을 빼고 "분포" 자체만 보여줄 것.
-- =============================================================================

-- (A) 퍼널 단계별 집계
WITH funnel AS (
    SELECT
        r.referee_id,
        r.status AS referral_status,
        u.is_student_verified,
        (SELECT COUNT(*) FROM selections s WHERE s.selector_id = r.referee_id) AS referee_selections
    FROM referral_events r
    JOIN users u ON u.id = r.referee_id
)
SELECT
    COUNT(*) AS step_1_invited,
    COUNT(*) FILTER (WHERE is_student_verified = true) AS step_2_verified,
    COUNT(*) FILTER (WHERE referral_status = 'REWARDED') AS step_3_rewarded,
    COUNT(*) FILTER (WHERE referee_selections > 0) AS step_4_active,
    ROUND(COUNT(*) FILTER (WHERE is_student_verified = true)::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS verify_rate_pct,
    ROUND(COUNT(*) FILTER (WHERE referral_status = 'REWARDED')::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS reward_rate_pct,
    ROUND(COUNT(*) FILTER (WHERE referee_selections > 0)::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS activation_rate_pct
FROM funnel;

-- (B) 초대자별 보상 적립 횟수 분포 상위 10
SELECT
    referrer_id,
    COUNT(*) AS invited_count,
    COUNT(*) FILTER (WHERE status = 'REWARDED') AS rewarded_count
FROM referral_events
GROUP BY referrer_id
ORDER BY rewarded_count DESC
LIMIT 10;
