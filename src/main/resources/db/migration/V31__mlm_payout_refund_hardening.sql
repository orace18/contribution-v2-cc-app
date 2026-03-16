CREATE UNIQUE INDEX IF NOT EXISTS uk_mlm_volume_ledger_order_account
    ON mlm_volume_ledger (source_order_id, account_holder_id);

ALTER TABLE commission_payout_items
    ADD COLUMN IF NOT EXISTS commission_id UUID;

ALTER TABLE commission_payout_items
    DROP CONSTRAINT IF EXISTS uk_commission_payout_items_batch_account_holder;

UPDATE commission_payout_items cpi
SET commission_id = mc.id
FROM mlm_commissions mc
WHERE mc.payout_item_id = cpi.id
  AND cpi.commission_id IS NULL;

ALTER TABLE commission_payout_items
    DROP CONSTRAINT IF EXISTS fk_commission_payout_items_commission;

ALTER TABLE commission_payout_items
    ADD CONSTRAINT fk_commission_payout_items_commission
        FOREIGN KEY (commission_id) REFERENCES mlm_commissions(id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_commission_payout_items_commission
    ON commission_payout_items (commission_id)
    WHERE commission_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_commission_payout_items_commission
    ON commission_payout_items (commission_id);

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_amount_positive;

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_amount_non_zero;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_amount_non_zero
        CHECK (amount <> 0);

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_commission_type;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_commission_type
        CHECK (commission_type IN ('SUBSCRIPTION', 'GROUP_BONUS', 'REFUND_REVERSAL'));

ALTER TABLE shop_orders
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ;

ALTER TABLE shop_orders
    DROP CONSTRAINT IF EXISTS ck_shop_orders_status;

ALTER TABLE shop_orders
    ADD CONSTRAINT ck_shop_orders_status
        CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'));

CREATE TABLE IF NOT EXISTS commission_adjustments (
    id UUID PRIMARY KEY,
    reference_order_id UUID NOT NULL,
    account_holder_id UUID,
    adjustment_type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_commission_adjustments_reference_order
        FOREIGN KEY (reference_order_id) REFERENCES shop_orders(id),
    CONSTRAINT fk_commission_adjustments_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_commission_adjustments_type
        CHECK (adjustment_type IN ('MERCHANT_PAYOUT_REVERSAL', 'MLM_COMMISSION_REVERSAL', 'MLM_VOLUME_REVERSAL'))
);

CREATE INDEX IF NOT EXISTS idx_commission_adjustments_order
    ON commission_adjustments (reference_order_id);

CREATE INDEX IF NOT EXISTS idx_commission_adjustments_account_holder
    ON commission_adjustments (account_holder_id);

CREATE INDEX IF NOT EXISTS idx_commission_adjustments_created_at
    ON commission_adjustments (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mlm_relationships_ancestor_created_descendant
    ON mlm_relationships (ancestor_id, created_at, descendant_id);

ALTER TABLE mlm_weekly_bonus_job_runs
    DROP CONSTRAINT IF EXISTS ck_mlm_weekly_bonus_job_runs_status;

ALTER TABLE mlm_weekly_bonus_job_runs
    ADD CONSTRAINT ck_mlm_weekly_bonus_job_runs_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'));
