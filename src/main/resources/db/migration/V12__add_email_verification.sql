ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL;

-- Users created before account verification existed keep logging in
UPDATE users SET email_verified_at = created_at;

UPDATE users SET email = LOWER(TRIM(email));

ALTER TABLE users ADD CONSTRAINT users_email_normalized CHECK (email = LOWER(TRIM(email)));

CREATE TABLE email_verification_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(user_id),
    UNIQUE(token_hash),

    FOREIGN KEY (user_id) REFERENCES users(id)
);
