ALTER TABLE user_devices
    ADD COLUMN IF NOT EXISTS push_token VARCHAR(512);

ALTER TABLE users
    ALTER COLUMN kyc_status TYPE VARCHAR(32);

ALTER TABLE account_holders
    ALTER COLUMN kyc_status TYPE VARCHAR(32);

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS ck_account_holders_kyc_status;

ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_kyc_status CHECK (
        kyc_status IN ('NOT_STARTED', 'PENDING', 'VERIFIED', 'REJECTED', 'RESUBMIT_REQUIRED')
    );
