-- 쪽지 수락/거절 기능 추가: 수락 시 양방향 연락처 공개, 거절 시 발신자 익명 보호

ALTER TABLE notes ADD COLUMN IF NOT EXISTS status VARCHAR(10) NOT NULL DEFAULT 'PENDING';
ALTER TABLE notes ADD COLUMN IF NOT EXISTS responded_at TIMESTAMP;

-- 발신자가 보낸 쪽지 조회 최적화
CREATE INDEX IF NOT EXISTS idx_notes_selector ON notes(selector_id);
