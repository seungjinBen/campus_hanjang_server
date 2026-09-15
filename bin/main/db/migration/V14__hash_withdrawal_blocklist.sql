-- 탈퇴 블록리스트의 kakao_id를 HMAC-SHA256 해시로 저장 (평문 카카오 식별자 보존 금지)
-- 기존 평문 행은 해시와 대조할 수 없으므로 삭제한다 (출시 전 — 테스트 데이터만 존재)
DELETE FROM withdrawal_blocklist;

ALTER TABLE withdrawal_blocklist RENAME COLUMN kakao_id TO kakao_id_hash;
ALTER TABLE withdrawal_blocklist ALTER COLUMN kakao_id_hash TYPE VARCHAR(64);
ALTER INDEX idx_withdrawal_blocklist_kakao_id RENAME TO idx_withdrawal_blocklist_kakao_id_hash;
