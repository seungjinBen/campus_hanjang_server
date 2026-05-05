-- 개발용 로컬 로그인 — 실서비스 전 이 마이그레이션과 관련 코드 삭제 예정
ALTER TABLE users
    ADD COLUMN email VARCHAR(100) UNIQUE,
    ADD COLUMN password_hash VARCHAR(255);

CREATE INDEX idx_users_email ON users(email);
