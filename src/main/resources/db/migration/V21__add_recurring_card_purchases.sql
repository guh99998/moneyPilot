-- Spec 016 (emenda): compra recorrente no cartao. So adiciona; nenhum dado existente muda.
CREATE TABLE recurring_purchases (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    day_of_month INTEGER NOT NULL CHECK (day_of_month >= 1 AND day_of_month <= 31),
    start_date DATE NOT NULL,
    end_date DATE NULL CHECK (end_date IS NULL OR end_date > start_date),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    generated_until DATE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_recurring_purchases_user ON recurring_purchases (user_id);
CREATE INDEX idx_recurring_purchases_account ON recurring_purchases (account_id);

ALTER TABLE transactions ADD COLUMN recurring_purchase_id BIGINT NULL REFERENCES recurring_purchases(id) ON DELETE SET NULL;
CREATE INDEX idx_transactions_recurring_purchase ON transactions (recurring_purchase_id) WHERE recurring_purchase_id IS NOT NULL;
