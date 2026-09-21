CREATE TABLE transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0 ),
    type VARCHAR(10) NOT NULL CHECK ( type IN ('INCOME', 'EXPENSE') ),
    description VARCHAR(255),
    date DATE NOT NULL,
    transfer_group_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT check_category_xor_transfer CHECK (
        (category_id IS NOT NULL AND transfer_group_id IS NULL) OR
        (category_id IS NULL AND transfer_group_id IS NOT NULL)
        ),

    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (category_id) REFERENCES categories(id)
);