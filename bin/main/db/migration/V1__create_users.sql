CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kakao_id VARCHAR(50) NOT NULL UNIQUE,
    nickname VARCHAR(20),
    gender VARCHAR(10),
    birth_date DATE,
    university VARCHAR(100),
    contact_type VARCHAR(20),
    contact_value_encrypted TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_login_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_kakao_id ON users(kakao_id);
CREATE INDEX idx_users_gender_active ON users(gender, is_active);
