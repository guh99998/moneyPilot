-- Spec 016: fatura do cartao. Apenas colunas nulas; nenhum dado existente muda.
ALTER TABLE accounts ADD COLUMN closing_day INTEGER;
ALTER TABLE accounts ADD COLUMN due_day INTEGER;
ALTER TABLE accounts ADD COLUMN invoices_from DATE;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_invoice_days CHECK (
    (closing_day IS NULL AND due_day IS NULL AND invoices_from IS NULL)
    OR (closing_day BETWEEN 1 AND 31 AND due_day BETWEEN 1 AND 31 AND invoices_from IS NOT NULL));

ALTER TABLE transactions ADD COLUMN installment_group_id UUID;
ALTER TABLE transactions ADD COLUMN installment_number INTEGER;
ALTER TABLE transactions ADD COLUMN installment_total INTEGER;
CREATE INDEX idx_transactions_installment_group ON transactions (installment_group_id) WHERE installment_group_id IS NOT NULL;
