-- V47: Add account_holder_id to country_chart_accounts
-- Each AccountHolder gets their own USER_WALLET ledger account.

ALTER TABLE country_chart_accounts
    ADD COLUMN IF NOT EXISTS account_holder_id UUID;

-- Partial unique index: only one active wallet account per (holder, country).
-- NULL account_holder_id rows (shared group accounts) are excluded from the constraint.
CREATE UNIQUE INDEX IF NOT EXISTS uk_country_chart_accounts_holder_country
    ON country_chart_accounts (account_holder_id, country_code)
    WHERE account_holder_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_holder_id
    ON country_chart_accounts (account_holder_id)
    WHERE account_holder_id IS NOT NULL;
