-- ============================================================
-- V75 – Link wallet reservations to operations
-- ============================================================

ALTER TABLE wallet_reservations
    ADD COLUMN IF NOT EXISTS operation_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_reservations_operation_id
    ON wallet_reservations (operation_id);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_operation_id
    ON wallet_reservations (operation_id);
