-- Spec 028: estorno de item da fatura. So adiciona; nenhum dado existente muda.
INSERT INTO categories(name, type, user_id) VALUES ('Estorno', 'INCOME', NULL);

ALTER TABLE transactions ADD COLUMN refund_of_transaction_id BIGINT NULL
    REFERENCES transactions(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_transactions_refund_of ON transactions (refund_of_transaction_id)
    WHERE refund_of_transaction_id IS NOT NULL;
