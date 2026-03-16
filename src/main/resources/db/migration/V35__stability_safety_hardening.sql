ALTER TABLE shop_orders
    DROP CONSTRAINT IF EXISTS ck_shop_orders_status;

ALTER TABLE shop_orders
    ADD CONSTRAINT ck_shop_orders_status
        CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_commission_type;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_commission_type
        CHECK (commission_type IN ('SUBSCRIPTION', 'GROUP_BONUS', 'ADJUSTMENT'));

ALTER TABLE commission_payout_items
    DROP CONSTRAINT IF EXISTS uk_commission_payout_items_batch_account_holder;

ALTER TABLE commission_payout_items
    ADD COLUMN IF NOT EXISTS commission_id UUID;

ALTER TABLE commission_payout_items
    DROP CONSTRAINT IF EXISTS fk_commission_payout_items_commission;

ALTER TABLE commission_payout_items
    ADD CONSTRAINT fk_commission_payout_items_commission
        FOREIGN KEY (commission_id) REFERENCES mlm_commissions(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_commission_payout_items_commission_id
    ON commission_payout_items (commission_id);

CREATE INDEX IF NOT EXISTS idx_commission_payout_items_commission_id
    ON commission_payout_items (commission_id);

CREATE TABLE IF NOT EXISTS commission_adjustments (
    id UUID PRIMARY KEY,
    reference_order_id UUID NOT NULL,
    account_holder_id UUID,
    adjustment_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_commission_adjustments_order
        FOREIGN KEY (reference_order_id) REFERENCES shop_orders(id),
    CONSTRAINT fk_commission_adjustments_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_commission_adjustments_amount_non_zero
        CHECK (amount <> 0)
);

CREATE INDEX IF NOT EXISTS idx_commission_adjustments_reference_order
    ON commission_adjustments (reference_order_id);

CREATE INDEX IF NOT EXISTS idx_commission_adjustments_account_holder
    ON commission_adjustments (account_holder_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_commission_adjustments_order_account_type
    ON commission_adjustments (reference_order_id, account_holder_id, adjustment_type);

CREATE TABLE IF NOT EXISTS mlm_placement_audit_logs (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    sponsor_account_holder_id UUID NOT NULL,
    placement_parent_id UUID NOT NULL,
    actor_user_id UUID,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mlm_placement_audit_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_mlm_placement_audit_sponsor
        FOREIGN KEY (sponsor_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_mlm_placement_audit_parent
        FOREIGN KEY (placement_parent_id) REFERENCES account_holders(id)
);

CREATE INDEX IF NOT EXISTS idx_mlm_placement_audit_logs_created_at
    ON mlm_placement_audit_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mlm_placement_audit_logs_account_holder
    ON mlm_placement_audit_logs (account_holder_id);

CREATE TABLE IF NOT EXISTS mlm_ledger_rebuild_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    requested_by_admin_id UUID,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    batch_size INTEGER NOT NULL,
    processed_orders BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(512),
    CONSTRAINT ck_mlm_ledger_rebuild_jobs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_mlm_ledger_rebuild_jobs_batch_size_positive
        CHECK (batch_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_mlm_ledger_rebuild_jobs_status_requested_at
    ON mlm_ledger_rebuild_jobs (status, requested_at);

ALTER TABLE mlm_weekly_bonus_job_runs
    DROP CONSTRAINT IF EXISTS ck_mlm_weekly_bonus_job_runs_status;

ALTER TABLE mlm_weekly_bonus_job_runs
    ADD CONSTRAINT ck_mlm_weekly_bonus_job_runs_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'));

CREATE TABLE IF NOT EXISTS chat_message_delivery_status (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    recipient_account_holder_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chat_delivery_status_message
        FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_delivery_status_recipient
        FOREIGN KEY (recipient_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_chat_delivery_status_message_recipient
        UNIQUE (message_id, recipient_account_holder_id),
    CONSTRAINT ck_chat_delivery_status_status
        CHECK (status IN ('SENT', 'DELIVERED', 'READ'))
);

CREATE INDEX IF NOT EXISTS idx_chat_delivery_status_message
    ON chat_message_delivery_status (message_id);

CREATE TABLE IF NOT EXISTS chat_read_receipts (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    reader_account_holder_id UUID NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chat_read_receipts_message
        FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_read_receipts_reader
        FOREIGN KEY (reader_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_chat_read_receipts_message_reader
        UNIQUE (message_id, reader_account_holder_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_read_receipts_message
    ON chat_read_receipts (message_id);
