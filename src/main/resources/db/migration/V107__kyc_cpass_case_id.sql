-- Persists the CPaaS KYC case identifier on the account holder so the
-- polling endpoint can enrich GET /kyc/status with the full CPaaS verdict
-- even after an app reload (the value is also exposed via GET /api/mobile/kyc/draft).
ALTER TABLE account_holders ADD COLUMN IF NOT EXISTS cpass_case_id VARCHAR(128);
