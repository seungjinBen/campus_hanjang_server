-- 2026 가을: 선택 횟수 개편 — 얼리버드 플래그 + 리퍼럴 초대 (CLAUDE.md 16-5)

ALTER TABLE users ADD COLUMN is_early_bird BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN referral_code VARCHAR(12) UNIQUE;
ALTER TABLE users ADD COLUMN referred_by UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE referral_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- referee UNIQUE: 피초대자는 평생 1회만 초대받을 수 있다 (초대자 바꿔치기 방지)
    referee_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    -- PENDING: 가입만 완료 / REWARDED: 학생인증 승인으로 보상 확정
    status VARCHAR(20) NOT NULL,
    rewarded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 초대자의 "오늘 보상 존재" 판정용 (리퍼럴 보너스는 rewarded_at 날짜 기준 당일만 유효)
CREATE INDEX idx_referral_events_referrer_rewarded ON referral_events(referrer_id, rewarded_at);
