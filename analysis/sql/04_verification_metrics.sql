-- =============================================================================
-- 04. 학생인증 LLM 에이전트 운영 지표 + confidence 임계값 역검증
-- =============================================================================
-- 목적:
--   (A) 전체 status 분포 (AUTO_APPROVED / NEEDS_REVIEW / REJECTED / RETRY_REQUESTED)
--   (B) 경로별(SEJONG_QR vs EVERYTIME_PROFILE) 성능 비교
--       → 2차 시즌 안드로이드 대응으로 추가한 두 번째 경로가 실제로 유의미했는지
--   (C) confidence 임계값 0.85 하향/상향 시 자동승인 전환 시뮬레이션
--   (D) processing_ms / llm_calls 분포 (비용 분석)
--
-- 민감 정보 처리:
--   extracted_name, extracted_student_no, extracted_birth_date, image_hash,
--   decision_reason, user_id 는 집계에 사용하지 않는다.
-- =============================================================================

-- (A)(B) status × 경로별 평균 지표
SELECT
    verification_method,
    status,
    COUNT(*) AS cnt,
    ROUND(AVG(confidence_score)::numeric, 3) AS avg_confidence,
    ROUND(AVG(processing_ms)::numeric, 0) AS avg_processing_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY processing_ms) AS p95_processing_ms,
    ROUND(AVG(llm_calls)::numeric, 2) AS avg_llm_calls,
    MAX(llm_calls) AS max_llm_calls
FROM student_verifications
GROUP BY verification_method, status
ORDER BY verification_method, status;

-- (C) confidence 임계값 변경 시 자동승인/검수 전환 시뮬레이션
--     현재 운영: confidence >= 0.85 + 다른 게이트(대학명·학번·중복) 통과 시 AUTO_APPROVED
--     여기서는 confidence 단독 영향만 본다 (다른 게이트는 통과 가정).
--     → "임계값을 낮추면 자동승인율이 얼마나 늘고 리스크 노출은 어떻게 변하나" 트레이드오프.
WITH thresholds AS (
    SELECT unnest(ARRAY[0.70, 0.75, 0.80, 0.85, 0.90, 0.95]) AS threshold
),
candidates AS (
    -- confidence로만 판정 뒤바뀔 수 있는 후보군 = 자동승인 + 검수대기
    SELECT confidence_score, status, verification_method
    FROM student_verifications
    WHERE status IN ('AUTO_APPROVED', 'NEEDS_REVIEW')
      AND confidence_score IS NOT NULL
)
SELECT
    t.threshold,
    c.verification_method,
    COUNT(*) AS candidate_pool,
    COUNT(*) FILTER (WHERE c.confidence_score >= t.threshold) AS would_auto_approve,
    COUNT(*) FILTER (WHERE c.confidence_score <  t.threshold) AS would_review,
    ROUND(COUNT(*) FILTER (WHERE c.confidence_score >= t.threshold)::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS auto_approve_pct
FROM thresholds t
CROSS JOIN candidates c
GROUP BY t.threshold, c.verification_method
ORDER BY c.verification_method, t.threshold;

-- (D) 비용/처리시간 raw (pandas 분포 분석용)
--   SELECT confidence_score, processing_ms, llm_calls, status, verification_method
--   FROM student_verifications WHERE confidence_score IS NOT NULL;
