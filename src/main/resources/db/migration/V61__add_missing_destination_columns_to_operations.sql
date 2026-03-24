-- V61 – Add missing destination columns to operations table
ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS destination_account_holder_id UUID,
    ADD COLUMN IF NOT EXISTS destination_wallet_id         UUID,
    ADD COLUMN IF NOT EXISTS destination_phone             VARCHAR(64);
