-- =============================================================================
-- 03. 매칭 임계값(0.75) 역검증 — 백테스트
-- =============================================================================
-- 목적: selections.match_score 분포는 그대로 두고 임계값을 0.60~0.90으로 바꿨을 때
--       CONTACT_REVEALED vs NOTE_SENT 분기 비율이 어떻게 달라지는지 시뮬레이션.
--       → 1차에서 0.70 → 0.75로 올린 결정의 사후 검증 + 다른 선택지가 있었는지 확인.
--
-- 가정 (한계):
--   - 유저의 "선택 자체"는 임계값과 무관하게 동일하다고 가정하고 분기만 재분류.
--   - 실제로는 유저가 공개 가능성을 보고 전략적으로 선택할 수 있어 완벽한
--     counterfactual은 아님 → 자소서에 명시.
--
-- 산출: 임계값별 공개율/쪽지율 테이블 + match_score raw 분포(히스토그램용).
-- =============================================================================

-- (A) 임계값별 분기 비율 시뮬레이션
WITH thresholds AS (
    SELECT unnest(ARRAY[0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90]) AS threshold
)
SELECT
    t.threshold,
    COUNT(*) AS total_selections,
    COUNT(*) FILTER (WHERE s.match_score >= t.threshold) AS would_reveal,
    COUNT(*) FILTER (WHERE s.match_score <  t.threshold) AS would_note,
    ROUND(COUNT(*) FILTER (WHERE s.match_score >= t.threshold)::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS reveal_pct,
    ROUND(COUNT(*) FILTER (WHERE s.match_score <  t.threshold)::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS note_pct
FROM thresholds t
CROSS JOIN selections s
WHERE s.match_score IS NOT NULL
GROUP BY t.threshold
ORDER BY t.threshold;

-- (B) match_score raw 분포 (pandas 히스토그램용 — 별도 CSV로 export)
--   SELECT match_score, type, DATE(created_at) AS d
--   FROM selections
--   WHERE match_score IS NOT NULL;
