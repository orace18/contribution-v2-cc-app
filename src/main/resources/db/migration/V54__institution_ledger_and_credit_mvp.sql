ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS institution_id UUID,
    ADD COLUMN IF NOT EXISTS disbursement_reference_id VARCHAR(128);

ALTER TABLE loans
    ADD CONSTRAINT fk_loans_institution
        FOREIGN KEY (institution_id) REFERENCES financial_partners(id);

CREATE INDEX IF NOT EXISTS idx_loans_institution_status
    ON loans (institution_id, status);

ALTER TABLE loan_repayments
    ADD COLUMN IF NOT EXISTS institution_id UUID;

ALTER TABLE loan_repayments
    ADD CONSTRAINT fk_loan_repayments_institution
        FOREIGN KEY (institution_id) REFERENCES financial_partners(id);

CREATE INDEX IF NOT EXISTS idx_loan_repayments_institution_paid_at
    ON loan_repayments (institution_id, paid_at);

CREATE TABLE IF NOT EXISTS institution_ledger_entries (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    institution_id UUID NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_institution_ledger_entries_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_institution_ledger_entries_institution
        FOREIGN KEY (institution_id) REFERENCES financial_partners(id),
    CONSTRAINT uk_institution_ledger_entries_reference_id
        UNIQUE (reference_id),
    CONSTRAINT ck_institution_ledger_entries_type
        CHECK (entry_type IN ('LOAN', 'REPAYMENT', 'SAVING')),
    CONSTRAINT ck_institution_ledger_entries_amount
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_institution_ledger_entries_user_created
    ON institution_ledger_entries (account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_institution_ledger_entries_institution_created
    ON institution_ledger_entries (institution_id, created_at DESC);
