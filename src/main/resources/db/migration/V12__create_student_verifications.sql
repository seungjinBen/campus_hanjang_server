-- 학생인증 AI 에이전트: 인증 기록 + 학과 동적 사전 + 유저 인증/필터 컬럼

CREATE TABLE student_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,

    -- 에이전트 추출 결과
    extracted_university VARCHAR(100),
    extracted_name VARCHAR(50),
    extracted_student_no VARCHAR(20),
    extracted_department VARCHAR(100),
    extracted_birth_date DATE,

    -- 에이전트 판정 정보
    confidence_score DOUBLE PRECISION,
    decision_reason VARCHAR(500),
    agent_actions JSONB,

    -- 어뷰징 방지: 동일 캡처 재사용 차단
    image_hash VARCHAR(64) NOT NULL,

    -- 메트릭 (자동승인율·처리시간·비용 집계용)
    processing_ms INT,
    llm_calls INT,

    -- 수동 검수
    reviewed_by UUID,
    reviewed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 승인 상태의 학번만 유니크 — 거절/재시도 이력은 중복 허용 (1학번 1계정)
CREATE UNIQUE INDEX uq_student_verifications_approved_student_no
    ON student_verifications(extracted_student_no)
    WHERE status IN ('AUTO_APPROVED', 'APPROVED');

CREATE INDEX idx_student_verifications_user_id ON student_verifications(user_id);
CREATE INDEX idx_student_verifications_status ON student_verifications(status);
CREATE INDEX idx_student_verifications_image_hash ON student_verifications(image_hash);

-- 학과 동적 사전 — 학년별 개편으로 과 이름이 바뀌므로 고정 목록 대신 승인 시 자동 등록
CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 유저 인증 상태 + 매칭 카드 풀 학과 필터 모드 (ALL / SAME_ONLY / EXCLUDE_SAME)
ALTER TABLE users ADD COLUMN is_student_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN verified_university VARCHAR(100);
ALTER TABLE users ADD COLUMN verified_department VARCHAR(100);
ALTER TABLE users ADD COLUMN dept_filter_mode VARCHAR(20) NOT NULL DEFAULT 'ALL';
