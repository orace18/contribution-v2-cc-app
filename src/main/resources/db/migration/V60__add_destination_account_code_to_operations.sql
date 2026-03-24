-- V60 – Add missing destination_account_code column to operations table
ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS destination_account_code VARCHAR(64);
