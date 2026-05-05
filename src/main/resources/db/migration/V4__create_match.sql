CREATE TABLE match_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    next_available_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 유저당 PENDING 세션 1개 제한
CREATE UNIQUE INDEX uq_user_pending_session
    ON match_sessions(user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_session_user_status ON match_sessions(user_id, status);
CREATE INDEX idx_session_expire ON match_sessions(next_available_at, status);

CREATE TABLE match_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    match_score FLOAT NOT NULL,
    is_chosen BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_candidate_session FOREIGN KEY (session_id) REFERENCES match_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_user FOREIGN KEY (candidate_id) REFERENCES users(id)
);

CREATE TABLE selections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    selector_id UUID NOT NULL,
    selected_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_selection_selector FOREIGN KEY (selector_id) REFERENCES users(id),
    CONSTRAINT fk_selection_selected FOREIGN KEY (selected_id) REFERENCES users(id)
);

CREATE INDEX idx_selections_selected ON selections(selected_id);
