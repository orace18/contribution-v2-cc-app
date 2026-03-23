ALTER TABLE shop_orders
    ADD COLUMN IF NOT EXISTS merchant_id UUID;

ALTER TABLE shop_orders
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

ALTER TABLE shop_orders
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

ALTER TABLE shop_orders
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

ALTER TABLE shop_orders
    ADD CONSTRAINT fk_shop_orders_merchant
        FOREIGN KEY (merchant_id) REFERENCES account_holders(id);

CREATE INDEX IF NOT EXISTS idx_shop_orders_merchant_created
    ON shop_orders (merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_orders_country_status
    ON shop_orders (country_code, status, created_at DESC);

ALTER TABLE mlm_volume_ledger
    ADD COLUMN IF NOT EXISTS tree_type VARCHAR(16);

ALTER TABLE mlm_volume_ledger
    ADD COLUMN IF NOT EXISTS generation INTEGER;

ALTER TABLE mlm_volume_ledger
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE mlm_volume_ledger
SET tree_type = COALESCE(tree_type, 'PLACEMENT'),
    generation = COALESCE(generation, 0)
WHERE tree_type IS NULL
   OR generation IS NULL;

ALTER TABLE mlm_volume_ledger
    ALTER COLUMN tree_type SET NOT NULL;

ALTER TABLE mlm_volume_ledger
    ALTER COLUMN generation SET NOT NULL;

ALTER TABLE mlm_volume_ledger
    ALTER COLUMN leg_position DROP NOT NULL;

ALTER TABLE mlm_volume_ledger
    DROP CONSTRAINT IF EXISTS uk_mlm_volume_ledger_account_order;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mlm_volume_ledger_account_order_tree
    ON mlm_volume_ledger (account_holder_id, source_order_id, tree_type);
