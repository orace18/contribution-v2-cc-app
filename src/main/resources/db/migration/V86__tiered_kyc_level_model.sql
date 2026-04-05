ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS kyc_level VARCHAR(32) NOT NULL DEFAULT 'LEVEL_0_UNVERIFIED',
    ADD COLUMN IF NOT EXISTS birth_date DATE,
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(128);

UPDATE account_holders
SET kyc_level = CASE
    WHEN kyc_status = 'VERIFIED' THEN 'LEVEL_1_BASIC'
    ELSE 'LEVEL_0_UNVERIFIED'
END
WHERE kyc_level IS NULL
   OR kyc_level NOT IN ('LEVEL_0_UNVERIFIED', 'LEVEL_1_BASIC', 'LEVEL_2_ENHANCED', 'LEVEL_3_FULL');

ALTER TABLE account_holders
    ALTER COLUMN kyc_level SET DEFAULT 'LEVEL_0_UNVERIFIED';

ALTER TABLE wallet_limit_configs
    ALTER COLUMN kyc_level TYPE VARCHAR(32);

UPDATE wallet_limit_configs
SET kyc_level = CASE
    WHEN kyc_level = 'VERIFIED' THEN 'LEVEL_1_BASIC'
    ELSE 'LEVEL_0_UNVERIFIED'
END
WHERE kyc_level IN ('NOT_STARTED', 'PENDING', 'VERIFIED', 'REJECTED', 'RESUBMIT_REQUIRED');

ALTER TABLE wallet_limit_configs
    DROP CONSTRAINT IF EXISTS ck_wallet_limit_configs_kyc_level;

ALTER TABLE wallet_limit_configs
    ADD CONSTRAINT ck_wallet_limit_configs_kyc_level CHECK (
        kyc_level IN ('LEVEL_0_UNVERIFIED', 'LEVEL_1_BASIC', 'LEVEL_2_ENHANCED', 'LEVEL_3_FULL')
    );
