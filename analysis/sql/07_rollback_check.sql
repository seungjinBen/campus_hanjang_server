-- =============================================================================
-- 07. 자동승인 사후 롤백 레코드 확인
-- =============================================================================
-- 목적: "자동승인 193건 중 관리자가 뒤집은(롤백한) 건이 0건"임을 레코드 레벨로 확인.
--       이력서/자소서의 "사후 롤백 0건" 주장에 대한 레코드 레벨 근거 확보.
--
-- 추적 원리:
--   테이블에 상태 전이 이력(audit log)이 없으므로 롤백은 reviewed_at/reviewed_by로만 추적.
--   - 자동승인 흐름은 reviewed_at을 세팅하지 않음 → AUTO_APPROVED 레코드의 reviewed_at은 null이어야 정상
--   - 관리자가 자동승인 결과를 뒤집으면 status가 바뀌면서 reviewed_at/reviewed_by가 찍힘
--   → "AUTO_APPROVED 상태의 reviewed_at = 0건"이면 "관리자 손 안 댐" = 롤백 0건
-- =============================================================================

-- (A) 핵심 쿼리 — 자동승인 건 중 관리자가 손댄 흔적이 있는지
--     기대 결과: cnt=193, has_reviewed_at=0, has_reviewed_by=0
SELECT
    status,
    COUNT(*) AS cnt,
    COUNT(*) FILTER (WHERE reviewed_at IS NOT NULL) AS has_reviewed_at,
    COUNT(*) FILTER (WHERE reviewed_by IS NOT NULL) AS has_reviewed_by
FROM student_verifications
WHERE status = 'AUTO_APPROVED'
GROUP BY status;

-- (B) 전체 상태 × 경로별 reviewed 분포 — 관리자 처리 흐름 전수 파악용
--     NEEDS_REVIEW/REJECTED/APPROVED 상태에서만 reviewed_at이 찍혀있어야 정상
SELECT
    verification_method,
    status,
    COUNT(*) AS cnt,
    COUNT(*) FILTER (WHERE reviewed_at IS NOT NULL) AS has_reviewed_at,
    COUNT(*) FILTER (WHERE reviewed_by IS NOT NULL) AS has_reviewed_by
FROM student_verifications
GROUP BY verification_method, status
ORDER BY verification_method, status;

-- (C) 혹시라도 자동승인에 reviewed_at이 찍힌 건이 있다면 상세 조회
--     결과 행이 있으면 그 건이 "관리자 재확인한 자동승인" — 롤백 여부는 수동 확인 필요
SELECT
    id,
    verification_method,
    confidence_score,
    created_at,
    reviewed_at,
    reviewed_by
FROM student_verifications
WHERE status = 'AUTO_APPROVED'
  AND (reviewed_at IS NOT NULL OR reviewed_by IS NOT NULL);
