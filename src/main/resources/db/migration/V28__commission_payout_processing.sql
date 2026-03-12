CREATE TABLE IF NOT EXISTS commission_payout_batches (
    id UUID PRIMARY KEY,
    week_id INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_commission_payout_batches_week UNIQUE (week_id),
    CONSTRAINT ck_commission_payout_batches_status
        CHECK (status IN ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_commission_payout_batches_status
    ON commission_payout_batches (status);

CREATE TABLE IF NOT EXISTS commission_payout_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operation_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    failure_reason VARCHAR(512),
    CONSTRAINT fk_commission_payout_items_batch
        FOREIGN KEY (batch_id) REFERENCES commission_payout_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_commission_payout_items_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_commission_payout_items_operation
        FOREIGN KEY (operation_id) REFERENCES operations(id),
    CONSTRAINT uk_commission_payout_items_batch_account_holder UNIQUE (batch_id, account_holder_id),
    CONSTRAINT ck_commission_payout_items_status
        CHECK (status IN ('CREATED', 'PAID', 'FAILED')),
    CONSTRAINT ck_commission_payout_items_amounts_non_negative
        CHECK (gross_amount >= 0 AND tax_amount >= 0 AND net_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_commission_payout_items_batch
    ON commission_payout_items (batch_id);

CREATE INDEX IF NOT EXISTS idx_commission_payout_items_status
    ON commission_payout_items (status);

ALTER TABLE mlm_commissions
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS payout_batch_id UUID,
    ADD COLUMN IF NOT EXISTS payout_item_id UUID;

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_status;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_status
        CHECK (status IN ('PENDING', 'PAID'));

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS fk_mlm_commissions_payout_batch;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT fk_mlm_commissions_payout_batch
        FOREIGN KEY (payout_batch_id) REFERENCES commission_payout_batches(id);

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS fk_mlm_commissions_payout_item;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT fk_mlm_commissions_payout_item
        FOREIGN KEY (payout_item_id) REFERENCES commission_payout_items(id);

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_week_type_status
    ON mlm_commissions (week_id, commission_type, status);

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_payout_batch
    ON mlm_commissions (payout_batch_id);

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_payout_item
    ON mlm_commissions (payout_item_id);
