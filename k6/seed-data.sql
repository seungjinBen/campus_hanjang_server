-- ============================================================
-- CampusHanjang k6 부하 테스트 시드 데이터
-- 사용법: psql -U bsjworld -d campus -f k6/seed-data.sql
-- 멱등성: ON CONFLICT DO NOTHING 처리로 중복 실행 안전
--
-- 생성 데이터:
--   남성 유저 500명 (kakao_id: test_male_001 ~ test_male_500)
--   여성 유저 500명 (kakao_id: test_female_001 ~ test_female_500)
--   사진 1000장, 특징 각 5개, 이상형 조건 각 3개
--   오늘 카드: 남성 유저 500명 × 10장 = 5000장 (카드 생성 오버헤드 제거)
--   쪽지: 여성 유저 100명에게 1건씩 (수신함 N+1 테스트용)
--   role = 'ADMIN' → 매칭 기능 종료 이후에도 접근 가능
-- ============================================================

DO $$
DECLARE
    male_ids   UUID[];
    female_ids UUID[];
    v_id       UUID;

    mbti_vals   TEXT[] := ARRAY['INTJ','INTP','ENTJ','ENTP','INFJ','INFP','ENFJ','ENFP',
                                'ISTJ','ISFJ','ESTJ','ESFJ','ISTP','ISFP','ESTP','ESFP'];
    hobby_vals  TEXT[] := ARRAY['독서','영화감상','운동','게임','음악감상',
                                '요리','여행','그림그리기','사진촬영','카페탐방'];
    animal_vals TEXT[] := ARRAY['강아지상','고양이상','토끼상','여우상','곰상','사슴상'];
    major_vals  TEXT[] := ARRAY['컴퓨터공학','경영학','심리학','건축학','화학공학',
                                '영문학','수학','물리학','의학','법학'];
    univ_vals   TEXT[] := ARRAY['서울대학교','연세대학교','고려대학교','성균관대학교','한양대학교',
                                '이화여자대학교','중앙대학교','경희대학교','홍익대학교','건국대학교'];
    female_cand INT;
    male_cand   INT;

BEGIN
    -- ================================================================
    -- 남성 테스트 유저 500명
    -- ================================================================
    FOR i IN 1..500 LOOP
        INSERT INTO users (kakao_id, nickname, gender, birth_date, university,
                           contact_type, is_active, role, daily_select_count,
                           created_at, last_login_at)
        VALUES (
            'test_male_' || LPAD(i::text, 3, '0'),
            '테스트남' || LPAD(i::text, 3, '0'),
            'MALE',
            '2000-01-01'::date + ((i % 5) * 365),
            univ_vals[1 + (i % array_length(univ_vals, 1))],
            'INSTAGRAM',
            true, 'ADMIN', 0,
            now(), now()
        )
        ON CONFLICT (kakao_id) DO NOTHING;

        SELECT id INTO v_id FROM users
        WHERE kakao_id = 'test_male_' || LPAD(i::text, 3, '0');
        male_ids[i] := v_id;

        -- 사진
        INSERT INTO user_photos (user_id, storage_url, thumbnail_url, created_at)
        VALUES (
            v_id,
            'https://storage.example.com/profiles/male_' || i || '.jpg',
            'https://storage.example.com/thumbnails/male_' || i || '.jpg',
            now()
        )
        ON CONFLICT (user_id) DO NOTHING;

        -- 특징 5개 (HEIGHT, MBTI, HOBBY, MAJOR, ANIMAL_FACE)
        INSERT INTO user_traits (user_id, trait_key, trait_value, is_visible) VALUES
            (v_id, 'HEIGHT',      (168 + (i % 15))::text || 'cm',                                  true),
            (v_id, 'MBTI',        mbti_vals[1 + (i % array_length(mbti_vals, 1))],                 true),
            (v_id, 'HOBBY',       hobby_vals[1 + (i % array_length(hobby_vals, 1))],               true),
            (v_id, 'MAJOR',       major_vals[1 + (i % array_length(major_vals, 1))],               true),
            (v_id, 'ANIMAL_FACE', animal_vals[1 + (i % array_length(animal_vals, 1))],             true)
        ON CONFLICT (user_id, trait_key) DO NOTHING;

        -- 이상형 조건 3개
        INSERT INTO ideal_traits (user_id, trait_key, trait_value) VALUES
            (v_id, 'MBTI',  mbti_vals[1 + ((i + 3) % array_length(mbti_vals, 1))]),
            (v_id, 'HOBBY', hobby_vals[1 + ((i + 2) % array_length(hobby_vals, 1))]),
            (v_id, 'HEIGHT', (155 + (i % 15))::text || 'cm')
        ON CONFLICT (user_id, trait_key) DO NOTHING;
    END LOOP;

    -- ================================================================
    -- 여성 테스트 유저 500명
    -- ================================================================
    FOR i IN 1..500 LOOP
        INSERT INTO users (kakao_id, nickname, gender, birth_date, university,
                           contact_type, is_active, role, daily_select_count,
                           created_at, last_login_at)
        VALUES (
            'test_female_' || LPAD(i::text, 3, '0'),
            '테스트녀' || LPAD(i::text, 3, '0'),
            'FEMALE',
            '2001-01-01'::date + ((i % 5) * 365),
            univ_vals[1 + ((i + 2) % array_length(univ_vals, 1))],
            'PHONE',
            true, 'ADMIN', 0,
            now(), now()
        )
        ON CONFLICT (kakao_id) DO NOTHING;

        SELECT id INTO v_id FROM users
        WHERE kakao_id = 'test_female_' || LPAD(i::text, 3, '0');
        female_ids[i] := v_id;

        -- 사진
        INSERT INTO user_photos (user_id, storage_url, thumbnail_url, created_at)
        VALUES (
            v_id,
            'https://storage.example.com/profiles/female_' || i || '.jpg',
            'https://storage.example.com/thumbnails/female_' || i || '.jpg',
            now()
        )
        ON CONFLICT (user_id) DO NOTHING;

        -- 특징 5개
        INSERT INTO user_traits (user_id, trait_key, trait_value, is_visible) VALUES
            (v_id, 'HEIGHT',      (155 + (i % 15))::text || 'cm',                                  true),
            (v_id, 'MBTI',        mbti_vals[1 + ((i + 1) % array_length(mbti_vals, 1))],           true),
            (v_id, 'HOBBY',       hobby_vals[1 + ((i + 3) % array_length(hobby_vals, 1))],         true),
            (v_id, 'MAJOR',       major_vals[1 + ((i + 2) % array_length(major_vals, 1))],         true),
            (v_id, 'ANIMAL_FACE', animal_vals[1 + ((i + 2) % array_length(animal_vals, 1))],       true)
        ON CONFLICT (user_id, trait_key) DO NOTHING;

        -- 이상형 조건 3개
        INSERT INTO ideal_traits (user_id, trait_key, trait_value) VALUES
            (v_id, 'MBTI',   mbti_vals[1 + ((i + 5) % array_length(mbti_vals, 1))]),
            (v_id, 'HOBBY',  hobby_vals[1 + ((i + 4) % array_length(hobby_vals, 1))]),
            (v_id, 'HEIGHT', (168 + (i % 15))::text || 'cm')
        ON CONFLICT (user_id, trait_key) DO NOTHING;
    END LOOP;

    -- ================================================================
    -- 오늘 카드 사전 생성: 남성 500명 × 여성 후보 10장 = 5,000장
    -- 카드 생성 오버헤드를 제거해 순수 조회 N+1 성능만 측정
    -- ================================================================
    FOR i IN 1..500 LOOP
        FOR j IN 1..10 LOOP
            -- female_ids 인덱스: 1..500 범위 순환
            female_cand := 1 + ((i + j) % 500);
            IF female_cand = 0 THEN female_cand := 500; END IF;

            INSERT INTO daily_cards (user_id, candidate_id, match_score, card_date)
            VALUES (
                male_ids[i],
                female_ids[female_cand],
                0.40 + (random() * 0.55),
                CURRENT_DATE
            )
            ON CONFLICT (user_id, candidate_id, card_date) DO NOTHING;
        END LOOP;
    END LOOP;

    -- ================================================================
    -- 쪽지 수신함 테스트: 여성 유저 100명에게 각 1건
    -- getReceivedNotes N+1 측정용 (수신함이 비어있으면 N+1이 발생하지 않음)
    -- ================================================================
    FOR i IN 1..100 LOOP
        -- NOTE_SENT selection (중복 방지)
        IF NOT EXISTS (
            SELECT 1 FROM selections
            WHERE selector_id = male_ids[i]
              AND selected_id = female_ids[i]
              AND type = 'NOTE_SENT'
        ) THEN
            INSERT INTO selections (selector_id, selected_id, type, match_score, created_at)
            VALUES (
                male_ids[i],
                female_ids[i],
                'NOTE_SENT',
                0.40 + (random() * 0.29),  -- 0.40..0.69 (70% 미만 → 쪽지 유도)
                now() - (i || ' minutes')::interval
            );
        END IF;

        -- 쪽지 (중복 방지)
        IF NOT EXISTS (
            SELECT 1 FROM notes
            WHERE selector_id = male_ids[i]
              AND selected_id = female_ids[i]
        ) THEN
            INSERT INTO notes (selector_id, selected_id, note_content, status, created_at)
            VALUES (
                male_ids[i],
                female_ids[i],
                '안녕하세요! 잘 지내시나요? ' || i,
                'PENDING',
                now() - (i || ' minutes')::interval
            );
        END IF;
    END LOOP;

    RAISE NOTICE '시드 완료: 남성 500명, 여성 500명, 카드 5000장, 쪽지 100건';
END $$;
