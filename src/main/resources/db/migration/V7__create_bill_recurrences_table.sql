CREATE TABLE bill_recurrences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    category_id BIGINT NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0 ),
    type VARCHAR(10) NOT NULL CHECK ( type IN ('PAYABLE', 'RECEIVABLE') ),
    day_of_month INTEGER NOT NULL CHECK ( day_of_month >= 1 and day_of_month <= 31 ),
    start_date DATE NOT NULL,
    end_date DATE NULL CHECK ( end_date IS NULL OR end_date > start_date ),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (category_id) REFERENCES categories(id)
);