ALTER TABLE kyc_documents
    ADD COLUMN IF NOT EXISTS cpass_case_id VARCHAR(128);

ALTER TABLE kyc_documents
    ADD COLUMN IF NOT EXISTS cpass_submitted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_kyc_documents_account_holder_cpass_case
    ON kyc_documents(account_holder_id, cpass_case_id);
