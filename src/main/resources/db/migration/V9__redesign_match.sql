-- 일별 카드 제공 및 선택 횟수 제한 시스템으로 전면 개편

ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_select_count INT NOT NULL DEFAULT 0;

CREATE TABLE daily_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    match_score DOUBLE PRECISION NOT NULL,
    card_date DATE NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT fk_daily_card_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_card_candidate FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_daily_card UNIQUE (user_id, candidate_id, card_date)
);

CREATE INDEX idx_daily_cards_user_date ON daily_cards(user_id, card_date);

-- 기존 selections에 type, match_score 컬럼 추가 (기존 레코드는 NULL 허용)
ALTER TABLE selections ADD COLUMN IF NOT EXISTS type VARCHAR(20);
ALTER TABLE selections ADD COLUMN IF NOT EXISTS match_score DOUBLE PRECISION;

CREATE INDEX idx_selections_type ON selections(selected_id, type);

CREATE TABLE notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    selector_id UUID NOT NULL,
    selected_id UUID NOT NULL,
    note_content VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_note_selector FOREIGN KEY (selector_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_selected FOREIGN KEY (selected_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notes_selected ON notes(selected_id);
