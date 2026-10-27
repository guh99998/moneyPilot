CREATE TABLE dismissed_alerts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    alert_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(100) NOT NULL,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(user_id, alert_key),

    FOREIGN KEY (user_id) REFERENCES users(id)
);
