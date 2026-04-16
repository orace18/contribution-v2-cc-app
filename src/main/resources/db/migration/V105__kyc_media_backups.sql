CREATE TABLE IF NOT EXISTS kyc_media_backups (
    media_file_id UUID PRIMARY KEY REFERENCES media_files(id) ON DELETE CASCADE,
    account_holder_id UUID NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    payload BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kyc_media_backups_account_holder
    ON kyc_media_backups(account_holder_id);
