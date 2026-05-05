-- match_sessions: 유저 탈퇴 시 연관 세션 자동 삭제
ALTER TABLE match_sessions
    DROP CONSTRAINT fk_session_user,
    ADD CONSTRAINT fk_session_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- match_candidates: 후보로 지목된 유저가 탈퇴해도 후보 레코드 삭제
ALTER TABLE match_candidates
    DROP CONSTRAINT fk_candidate_user,
    ADD CONSTRAINT fk_candidate_user
        FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

-- selections: 선택자 또는 선택받은 유저 탈퇴 시 선택 이력 삭제
ALTER TABLE selections
    DROP CONSTRAINT fk_selection_selector,
    ADD CONSTRAINT fk_selection_selector
        FOREIGN KEY (selector_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE selections
    DROP CONSTRAINT fk_selection_selected,
    ADD CONSTRAINT fk_selection_selected
        FOREIGN KEY (selected_id) REFERENCES users(id) ON DELETE CASCADE;
