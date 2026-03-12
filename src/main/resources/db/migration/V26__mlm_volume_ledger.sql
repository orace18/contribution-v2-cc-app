CREATE TABLE IF NOT EXISTS mlm_volume_ledger (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    source_member_id UUID NOT NULL,
    source_order_id UUID NOT NULL,
    leg_position INTEGER NOT NULL,
    volume_cv NUMERIC(19,4) NOT NULL,
    week_id INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mlm_volume_ledger_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_mlm_volume_ledger_source_member
        FOREIGN KEY (source_member_id) REFERENCES account_holders(id),
    CONSTRAINT fk_mlm_volume_ledger_source_order
        FOREIGN KEY (source_order_id) REFERENCES shop_orders(id),
    CONSTRAINT uk_mlm_volume_ledger_account_order
        UNIQUE (account_holder_id, source_order_id),
    CONSTRAINT ck_mlm_volume_ledger_leg_position_positive
        CHECK (leg_position > 0),
    CONSTRAINT ck_mlm_volume_ledger_volume_non_negative
        CHECK (volume_cv >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mlm_volume_ledger_account_holder
    ON mlm_volume_ledger (account_holder_id);

CREATE INDEX IF NOT EXISTS idx_mlm_volume_ledger_week_id
    ON mlm_volume_ledger (week_id);

CREATE INDEX IF NOT EXISTS idx_mlm_volume_ledger_leg_position
    ON mlm_volume_ledger (leg_position);
