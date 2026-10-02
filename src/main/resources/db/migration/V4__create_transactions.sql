CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    reference_number VARCHAR(40) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    related_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_before DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);
