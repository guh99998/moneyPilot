CREATE TABLE bills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    category_id BIGINT NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL CHECK ( amount > 0 ),
    due_date DATE NOT NULL,
    type VARCHAR(10) NOT NULL CHECK ( type IN ('PAYABLE', 'RECEIVABLE') ),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN', CHECK ( status IN ('OPEN', 'SETTLED', 'CANCELED') ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    settled_at TIMESTAMPTZ NULL,
    settled_amount NUMERIC(19, 2) NULL CHECK ( settled_amount > 0 ),
    transaction_id BIGINT NULL,
    installment_group_id UUID NULL,
    installment_number INTEGER NULL,
    installment_total INTEGER NULL,
    recurrence_id BIGINT NULL,

    CHECK (
        (status = 'SETTLED' AND settled_at IS NOT NULL AND settled_amount IS NOT NULL AND transaction_id IS NOT NULL)
            OR
        (status <> 'SETTLED' AND settled_at IS NULL AND settled_amount IS NULL AND transaction_id IS NULL)
    ),

    CHECK (
        (installment_group_id IS NULL AND installment_number IS NULL AND installment_total IS NULL)
        OR
        (installment_group_id IS NOT NULL AND installment_number IS NOT NULL AND installment_total IS NOT NULL)
    ),

    CHECK(
        NOT(installment_group_id IS NOT NULL AND recurrence_id IS NOT NULL)
    ),

    UNIQUE(recurrence_id, due_date),

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    FOREIGN KEY (recurrence_id) REFERENCES bill_recurrences(id)
);

CREATE INDEX idx_bills_user_id_status_due_date ON bills(user_id, status, due_date);