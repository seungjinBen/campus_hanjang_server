CREATE TABLE user_traits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    trait_key VARCHAR(30) NOT NULL,
    trait_value VARCHAR(100) NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT fk_trait_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_trait UNIQUE (user_id, trait_key)
);

CREATE TABLE ideal_traits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    trait_key VARCHAR(30) NOT NULL,
    trait_value VARCHAR(100),
    CONSTRAINT fk_ideal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_ideal UNIQUE (user_id, trait_key)
);
