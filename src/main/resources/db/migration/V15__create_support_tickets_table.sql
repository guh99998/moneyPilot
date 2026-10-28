CREATE TABLE support_tickets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference VARCHAR(6) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    reply_to VARCHAR(255) NOT NULL,
    user_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    email_sent BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status_changed_at TIMESTAMPTZ NULL,

    CHECK (category IN ('DUVIDA', 'PROBLEMA', 'SUGESTAO', 'OUTRO')),
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),

    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_support_tickets_status_created_at ON support_tickets (status, created_at DESC);
