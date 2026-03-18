CREATE TABLE IF NOT EXISTS country_chart_accounts (
    id UUID PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(180) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    opening_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_country_chart_accounts_country_code_account_code
        UNIQUE (country_code, account_code),
    CONSTRAINT fk_country_chart_accounts_country
        FOREIGN KEY (country_code) REFERENCES countries(code),
    CONSTRAINT ck_country_chart_accounts_type
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    CONSTRAINT ck_country_chart_accounts_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_country_status
    ON country_chart_accounts (country_code, status);

CREATE INDEX IF NOT EXISTS idx_country_chart_accounts_country_code
    ON country_chart_accounts (country_code);

ALTER TABLE admin_audit_logs
    ADD COLUMN IF NOT EXISTS device_info VARCHAR(255);

WITH latest_accounting AS (
    SELECT DISTINCT ON (UPPER(country_code))
        UPPER(country_code) AS country_code,
        payload::jsonb AS payload_json
    FROM country_config_versions
    WHERE config_type = 'ACCOUNTING'
    ORDER BY UPPER(country_code), effective_date DESC, version DESC
),
account_entries AS (
    SELECT
        la.country_code,
        entry.key AS account_key,
        UPPER(BTRIM(entry.value)) AS account_code
    FROM latest_accounting la
    CROSS JOIN LATERAL jsonb_each_text(
        CASE
            WHEN jsonb_typeof(la.payload_json -> 'accounts') = 'object' THEN la.payload_json -> 'accounts'
            WHEN jsonb_typeof(la.payload_json -> 'preallocationAccounts') = 'object' THEN la.payload_json -> 'preallocationAccounts'
            ELSE '{}'::jsonb
        END
    ) AS entry
    WHERE entry.value IS NOT NULL
      AND BTRIM(entry.value) <> ''
),
dedup_accounts AS (
    SELECT DISTINCT country_code, account_key, account_code
    FROM account_entries
),
prepared_accounts AS (
    SELECT
        (
            SUBSTRING(MD5('country-chart-account:' || d.country_code || ':' || d.account_code), 1, 8) || '-' ||
            SUBSTRING(MD5('country-chart-account:' || d.country_code || ':' || d.account_code), 9, 4) || '-' ||
            SUBSTRING(MD5('country-chart-account:' || d.country_code || ':' || d.account_code), 13, 4) || '-' ||
            SUBSTRING(MD5('country-chart-account:' || d.country_code || ':' || d.account_code), 17, 4) || '-' ||
            SUBSTRING(MD5('country-chart-account:' || d.country_code || ':' || d.account_code), 21, 12)
        )::UUID AS id,
        d.country_code,
        d.account_code,
        INITCAP(REPLACE(REGEXP_REPLACE(d.account_key, '([A-Z])', ' \1', 'g'), '_', ' ')) AS account_name,
        CASE
            WHEN LOWER(d.account_key) LIKE '%commission%' THEN 'REVENUE'
            WHEN LOWER(d.account_key) LIKE '%fee%' THEN 'REVENUE'
            WHEN LOWER(d.account_key) LIKE '%penalt%' THEN 'REVENUE'
            WHEN LOWER(d.account_key) LIKE '%reserve%' THEN 'LIABILITY'
            WHEN LOWER(d.account_key) LIKE '%guarantee%' THEN 'LIABILITY'
            WHEN LOWER(d.account_key) LIKE '%social%' THEN 'LIABILITY'
            WHEN LOWER(d.account_key) LIKE '%admin%' THEN 'EQUITY'
            WHEN LOWER(d.account_key) LIKE '%platform%' THEN 'EQUITY'
            ELSE 'ASSET'
        END AS account_type,
        COALESCE(c.currency, 'USD') AS currency
    FROM dedup_accounts d
    LEFT JOIN countries c ON c.code = d.country_code
)
INSERT INTO country_chart_accounts (
    id,
    country_code,
    account_code,
    account_name,
    account_type,
    currency,
    opening_balance,
    status,
    created_at,
    updated_at
)
SELECT
    p.id,
    p.country_code,
    p.account_code,
    p.account_name,
    p.account_type,
    p.currency,
    0,
    'ACTIVE',
    NOW(),
    NOW()
FROM prepared_accounts p
ON CONFLICT ON CONSTRAINT uk_country_chart_accounts_country_code_account_code DO NOTHING;
