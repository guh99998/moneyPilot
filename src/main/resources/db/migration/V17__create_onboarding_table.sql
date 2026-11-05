-- Boas-vindas (spec 013): uma linha por conta que passou pelas boas-vindas. Conta antiga não tem linha.
CREATE TABLE onboarding (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    welcomed_at TIMESTAMPTZ NOT NULL,
    account_done_at TIMESTAMPTZ NULL,
    transaction_done_at TIMESTAMPTZ NULL,
    budget_done_at TIMESTAMPTZ NULL,
    dismissed_at TIMESTAMPTZ NULL
);
