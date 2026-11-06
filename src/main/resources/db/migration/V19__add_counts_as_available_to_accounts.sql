-- Spec 015: cada conta escolhe se conta no saldo disponivel. Investimento comeca fora do disponivel.
ALTER TABLE accounts ADD COLUMN counts_as_available BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE accounts SET counts_as_available = FALSE WHERE type = 'INVESTMENT';
