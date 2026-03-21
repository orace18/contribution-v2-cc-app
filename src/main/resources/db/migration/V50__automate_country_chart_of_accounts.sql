CREATE TABLE IF NOT EXISTS account_groups_master (
    group_id INT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    is_system_required BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO account_groups_master (group_id, name, type, description, is_system_required)
VALUES
    (10, 'Capital', 'LIABILITY', 'Core capital reserve for country activation', FALSE),
    (14, 'Fund Statutaire', 'LIABILITY', 'Statutory and circle reserve pool', TRUE),
    (46, 'Members Wallet', 'LIABILITY', 'Members wallet control account', TRUE),
    (47, 'PSP Clearing', 'ASSET', 'Payment service provider clearing account', TRUE),
    (52, 'Banks', 'ASSET', 'Bank cash and treasury accounts', FALSE),
    (54, 'Cash', 'ASSET', 'Cash-on-hand operational accounts', FALSE),
    (68, 'MLM Pool', 'LIABILITY', 'MLM commissions and bonus pool', TRUE),
    (70, 'Sales', 'REVENUE', 'Contribution and sales revenue accounts', FALSE),
    (72, 'Loan Portfolio', 'ASSET', 'Loan principal portfolio account', TRUE),
    (75, 'Fintech Revenue', 'REVENUE', 'Platform fees and fintech revenue', TRUE),
    (76, 'Interest Revenue', 'REVENUE', 'Interest income recognition account', TRUE)
ON CONFLICT (group_id) DO UPDATE
SET name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    is_system_required = EXCLUDED.is_system_required;

ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS master_group_id INT;

ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS system_tag VARCHAR(64);

ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS is_auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE country_chart_accounts
SET master_group_id = CASE account_group
        WHEN 'CAPITAL' THEN 10
        WHEN 'FUND_STATUTAIRE' THEN 14
        WHEN 'GUARANTEE_FUND' THEN 14
        WHEN 'USER_WALLET' THEN 46
        WHEN 'PSP_CLEARING' THEN 47
        WHEN 'BANKS' THEN 52
        WHEN 'CASH' THEN 54
        WHEN 'MLM_POOL' THEN 68
        WHEN 'COMMISSION_POOL' THEN 68
        WHEN 'SALES' THEN 70
        WHEN 'MEMBERSHIP_FEES' THEN 70
        WHEN 'LOAN_PORTFOLIO' THEN 72
        WHEN 'PARTNER_SETTLEMENT' THEN 72
        WHEN 'FINTECH_REVENUE' THEN 75
        WHEN 'PLATFORM_REVENUE' THEN 75
        WHEN 'INTEREST_REVENUE' THEN 76
        ELSE master_group_id
    END
WHERE master_group_id IS NULL;

UPDATE country_chart_accounts
SET system_tag = CASE master_group_id
        WHEN 14 THEN 'CIRCLE_POOL'
        WHEN 46 THEN CASE WHEN account_holder_id IS NULL THEN 'USER_WALLET' ELSE NULL END
        WHEN 47 THEN 'PSP_CLEARING'
        WHEN 68 THEN 'MLM_POOL'
        WHEN 72 THEN 'LOAN_PORTFOLIO'
        WHEN 75 THEN 'PLATFORM_REVENUE'
        WHEN 76 THEN 'INTEREST_REVENUE'
        ELSE system_tag
    END
WHERE system_tag IS NULL;

UPDATE country_chart_accounts
SET is_auto_generated = TRUE
WHERE is_auto_generated = FALSE
  AND (
      account_holder_id IS NOT NULL
      OR account_code ~ '^[0-9]+$'
      OR master_group_id IS NOT NULL
  );

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_country_chart_accounts_master_group'
    ) THEN
        ALTER TABLE country_chart_accounts
            ADD CONSTRAINT fk_country_chart_accounts_master_group
                FOREIGN KEY (master_group_id) REFERENCES account_groups_master (group_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_country_master_group
    ON country_chart_accounts (country_code, master_group_id);

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_country_system_tag
    ON country_chart_accounts (country_code, system_tag);

CREATE TABLE IF NOT EXISTS country_account_system_mapping (
    country_code VARCHAR(3) NOT NULL,
    system_tag VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL,
    PRIMARY KEY (country_code, system_tag),
    CONSTRAINT fk_country_account_system_mapping_account
        FOREIGN KEY (account_id) REFERENCES country_chart_accounts (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_country_account_system_mapping_account
    ON country_account_system_mapping (account_id);

INSERT INTO country_account_system_mapping (country_code, system_tag, account_id)
SELECT country_code, system_tag, id
FROM country_chart_accounts
WHERE system_tag IS NOT NULL
ON CONFLICT (country_code, system_tag) DO UPDATE
SET account_id = EXCLUDED.account_id;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_country_chart_accounts_group'
    ) THEN
        ALTER TABLE country_chart_accounts
            DROP CONSTRAINT ck_country_chart_accounts_group;
    END IF;
END
$$;

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
                    'CAPITAL',
                    'FUND_STATUTAIRE',
                    'USER_WALLET',
                    'PSP_CLEARING',
                    'BANKS',
                    'CASH',
                    'MLM_POOL',
                    'SALES',
                    'LOAN_PORTFOLIO',
                    'FINTECH_REVENUE',
                    'INTEREST_REVENUE',
                    'MEMBERSHIP_FEES',
                    'COMMISSION_POOL',
                    'PLATFORM_REVENUE',
                    'GUARANTEE_FUND',
                    'PARTNER_SETTLEMENT'
                ));
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_country_chart_accounts_system_tag'
    ) THEN
        ALTER TABLE country_chart_accounts
            ADD CONSTRAINT ck_country_chart_accounts_system_tag
                CHECK (
                    system_tag IS NULL
                    OR system_tag IN (
                        'USER_WALLET',
                        'PLATFORM_REVENUE',
                        'MLM_POOL',
                        'CIRCLE_POOL',
                        'LOAN_PORTFOLIO',
                        'INTEREST_REVENUE',
                        'PSP_CLEARING'
                    )
                );
    END IF;
END
$$;
