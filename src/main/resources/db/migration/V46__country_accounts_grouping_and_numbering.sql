ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS account_group VARCHAR(64);

ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS account_number VARCHAR(64);

UPDATE country_chart_accounts
SET account_group = CASE
        WHEN LOWER(account_name) LIKE '%membership%' OR LOWER(account_code) LIKE '%subscription%' THEN 'MEMBERSHIP_FEES'
        WHEN LOWER(account_name) LIKE '%commission%' OR LOWER(account_code) LIKE '%commission%' THEN 'COMMISSION_POOL'
        WHEN LOWER(account_name) LIKE '%guarantee%' OR LOWER(account_code) LIKE '%guarantee%' THEN 'GUARANTEE_FUND'
        WHEN LOWER(account_name) LIKE '%settlement%' OR LOWER(account_code) LIKE '%settlement%' THEN 'PARTNER_SETTLEMENT'
        WHEN LOWER(account_name) LIKE '%wallet%' OR LOWER(account_code) LIKE '%wallet%' THEN 'USER_WALLET'
        ELSE 'PLATFORM_REVENUE'
    END
WHERE account_group IS NULL;

UPDATE country_chart_accounts
SET account_number = UPPER(BTRIM(account_code))
WHERE account_number IS NULL
   OR BTRIM(account_number) = '';

UPDATE country_chart_accounts
SET account_code = UPPER(BTRIM(account_code)),
    account_number = UPPER(BTRIM(account_number));

ALTER TABLE country_chart_accounts
    ALTER COLUMN account_group SET DEFAULT 'PLATFORM_REVENUE';

ALTER TABLE country_chart_accounts
    ALTER COLUMN account_group SET NOT NULL;

ALTER TABLE country_chart_accounts
    ALTER COLUMN account_number SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_country_group
    ON country_chart_accounts (country_code, account_group);

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_account_number
    ON country_chart_accounts (account_number);

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_country_chart_accounts_group'
    ) THEN
        ALTER TABLE country_chart_accounts
            ADD CONSTRAINT ck_country_chart_accounts_group
                CHECK (account_group IN (
                    'MEMBERSHIP_FEES',
                    'COMMISSION_POOL',
                    'PLATFORM_REVENUE',
                    'GUARANTEE_FUND',
                    'PARTNER_SETTLEMENT',
                    'USER_WALLET'
                    ));
    END IF;
END
$$;
