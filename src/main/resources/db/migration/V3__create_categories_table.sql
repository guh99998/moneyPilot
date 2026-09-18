CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK ( type IN ('INCOME', 'EXPENSE') ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO categories(name, type, user_id) VALUES ('Salário', 'INCOME', NULL),
                                                   ('Freelance', 'INCOME', NULL),
                                                   ('Alimentação', 'EXPENSE', NULL),
                                                   ('Transporte', 'EXPENSE', NULL),
                                                   ('Moradia', 'EXPENSE', NULL),
                                                   ('Saúde', 'EXPENSE', NULL),
                                                   ('Lazer', 'EXPENSE', NULL),
                                                   ('Outros', 'EXPENSE', NULL);