-- V68: Sync DB check constraints with CountryAccountGroup and CountryAccountSystemTag Java enums.
-- account_group was missing: MARKETPLACE_POOL, ADMINISTRATION, SOCIAL_FUND
-- system_tag was missing: MARKETPLACE_POOL

-- ── account_group constraint ──────────────────────────────────────────────────

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_country_chart_accounts_group'
    ) THEN
        ALTER TABLE country_chart_accounts DROP CONSTRAINT ck_country_chart_accounts_group;
    END IF;
END
$$;

ALTER TABLE country_chart_accounts
    ADD CONSTRAINT ck_country_chart_accounts_group
        CHECK (account_group IN (
            'CAPITAL',
            'FUND_STATUTAIRE',
            'USER_WALLET',
            'PSP_CLEARING',
            'BANKS',
            'CASH',
            'MARKETPLACE_POOL',
            'MLM_POOL',
            'SALES',
            'LOAN_PORTFOLIO',
            'FINTECH_REVENUE',
            'INTEREST_REVENUE',
            'MEMBERSHIP_FEES',
            'COMMISSION_POOL',
            'PLATFORM_REVENUE',
            'ADMINISTRATION',
            'GUARANTEE_FUND',
            'SOCIAL_FUND',
            'PARTNER_SETTLEMENT'
        ));

-- ── system_tag constraint ─────────────────────────────────────────────────────

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_country_chart_accounts_system_tag'
    ) THEN
        ALTER TABLE country_chart_accounts DROP CONSTRAINT ck_country_chart_accounts_system_tag;
    END IF;
END
$$;

ALTER TABLE country_chart_accounts
    ADD CONSTRAINT ck_country_chart_accounts_system_tag
        CHECK (
            system_tag IS NULL
            OR system_tag IN (
                'USER_WALLET',
                'PLATFORM_REVENUE',
                'MARKETPLACE_POOL',
                'MLM_POOL',
                'CIRCLE_POOL',
                'LOAN_PORTFOLIO',
                'INTEREST_REVENUE',
                'PSP_CLEARING'
            )
        );
