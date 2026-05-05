CREATE TABLE user_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    storage_url TEXT NOT NULL,
    thumbnail_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_photo_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
