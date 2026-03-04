CREATE TABLE operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    psp_reference VARCHAR(128),
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_operations_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_operations_psp_reference UNIQUE (psp_reference),
    CONSTRAINT fk_operations_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_operations_user_id ON operations (user_id);
CREATE INDEX idx_operations_status ON operations (status);
CREATE INDEX idx_operations_psp_reference ON operations (psp_reference);

CREATE TABLE accounting_events (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_debit NUMERIC(19,4) NOT NULL,
    total_credit NUMERIC(19,4) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_accounting_events_operation_id UNIQUE (operation_id),
    CONSTRAINT fk_accounting_events_operation FOREIGN KEY (operation_id) REFERENCES operations(id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    line_no INTEGER NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_ledger_entries_event_line UNIQUE (event_id, line_no),
    CONSTRAINT fk_ledger_entries_event FOREIGN KEY (event_id) REFERENCES accounting_events(id)
);

CREATE INDEX idx_ledger_entries_account_currency ON ledger_entries (account_code, currency);

CREATE TABLE account_balance_snapshots (
    id UUID PRIMARY KEY,
    account_code VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_snapshots_account_currency UNIQUE (account_code, currency)
);

CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    psp_reference VARCHAR(128) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    matched BOOLEAN NOT NULL,
    adjustment_posted BOOLEAN NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_recon_batch_ref UNIQUE (batch_id, psp_reference)
);

CREATE INDEX idx_recon_batch_id ON reconciliation_records (batch_id);
CREATE INDEX idx_recon_psp_reference ON reconciliation_records (psp_reference);
