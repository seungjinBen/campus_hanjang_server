CREATE TABLE withdrawal_blocklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kakao_id VARCHAR(50) NOT NULL,
    withdrawn_at TIMESTAMP NOT NULL DEFAULT now(),
    reregistration_allowed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_withdrawal_blocklist_kakao_id ON withdrawal_blocklist(kakao_id);
