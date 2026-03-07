CREATE TABLE countries (
    id UUID PRIMARY KEY,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(120) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    is_international_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_countries_code UNIQUE (code),
    CONSTRAINT ck_countries_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_countries_international_fallback
    ON countries (is_international_fallback)
    WHERE is_international_fallback = TRUE;

CREATE INDEX idx_countries_status_code ON countries (status, code);

-- Mandatory international fallback country.
INSERT INTO countries (id, code, name, currency, status, is_international_fallback, created_at, updated_at)
VALUES ('11111111-1111-4111-8111-111111111111', 'INT', 'International', 'USD', 'ACTIVE', TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Seed country catalogue from existing admin and subscription configuration.
WITH source_countries AS (
    SELECT DISTINCT
           UPPER(ac.country_code) AS code,
           ac.name AS name,
           COALESCE(
               (SELECT UPPER(sp.currency)
                FROM subscription_plans sp
                WHERE UPPER(sp.country) = UPPER(ac.country_code)
                ORDER BY sp.created_at DESC
                LIMIT 1),
               'USD'
           ) AS currency
    FROM admin_countries ac
    WHERE ac.country_code IS NOT NULL
      AND LENGTH(TRIM(ac.country_code)) = 2
    UNION
    SELECT DISTINCT
           UPPER(sp.country) AS code,
           UPPER(sp.country) AS name,
           UPPER(sp.currency) AS currency
    FROM subscription_plans sp
    WHERE sp.country IS NOT NULL
      AND LENGTH(TRIM(sp.country)) = 2
),
hashed AS (
    SELECT code,
           name,
           currency,
           md5('country:' || code) AS hash
    FROM source_countries
)
INSERT INTO countries (id, code, name, currency, status, is_international_fallback, created_at, updated_at)
SELECT (SUBSTRING(hash, 1, 8) || '-' ||
        SUBSTRING(hash, 9, 4) || '-' ||
        SUBSTRING(hash, 13, 4) || '-' ||
        SUBSTRING(hash, 17, 4) || '-' ||
        SUBSTRING(hash, 21, 12))::UUID,
       code,
       name,
       currency,
       'ACTIVE',
       FALSE,
       NOW(),
       NOW()
FROM hashed
ON CONFLICT (code) DO NOTHING;

ALTER TABLE user_profiles
    ALTER COLUMN country TYPE VARCHAR(3);

ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

UPDATE account_holders ah
SET country_code = CASE
    WHEN up.country IS NOT NULL
         AND EXISTS (SELECT 1 FROM countries c WHERE c.code = UPPER(up.country))
        THEN UPPER(up.country)
    ELSE 'INT'
END
FROM user_profiles up
WHERE up.user_id = ah.id
  AND ah.country_code IS NULL;

UPDATE account_holders
SET country_code = 'INT'
WHERE country_code IS NULL;

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS fk_account_holders_country_code;
ALTER TABLE account_holders
    ADD CONSTRAINT fk_account_holders_country_code
        FOREIGN KEY (country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_account_holders_country_code ON account_holders (country_code);

ALTER TABLE circles
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

UPDATE circles c
SET country_code = COALESCE(ah.country_code, 'INT')
FROM account_holders ah
WHERE c.coach_id = ah.id
  AND c.country_code IS NULL;

UPDATE circles
SET country_code = 'INT'
WHERE country_code IS NULL;

ALTER TABLE circles
    ALTER COLUMN country_code SET NOT NULL;

ALTER TABLE circles
    DROP CONSTRAINT IF EXISTS fk_circles_country_code;
ALTER TABLE circles
    ADD CONSTRAINT fk_circles_country_code
        FOREIGN KEY (country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_circles_country_status ON circles (country_code, status);

ALTER TABLE capital_pools
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

UPDATE capital_pools cp
SET country_code = c.country_code
FROM circles c
WHERE cp.circle_id = c.id
  AND cp.country_code IS NULL;

UPDATE capital_pools
SET country_code = 'INT'
WHERE country_code IS NULL;

ALTER TABLE capital_pools
    ALTER COLUMN country_code SET NOT NULL;

ALTER TABLE capital_pools
    DROP CONSTRAINT IF EXISTS fk_capital_pools_country_code;
ALTER TABLE capital_pools
    ADD CONSTRAINT fk_capital_pools_country_code
        FOREIGN KEY (country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_capital_pools_country_code ON capital_pools (country_code);

ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

UPDATE loans l
SET country_code = COALESCE(ah.country_code, 'INT')
FROM account_holders ah
WHERE l.account_holder_id = ah.id
  AND l.country_code IS NULL;

UPDATE loans
SET country_code = 'INT'
WHERE country_code IS NULL;

ALTER TABLE loans
    ALTER COLUMN country_code SET NOT NULL;

ALTER TABLE loans
    DROP CONSTRAINT IF EXISTS fk_loans_country_code;
ALTER TABLE loans
    ADD CONSTRAINT fk_loans_country_code
        FOREIGN KEY (country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_loans_country_status ON loans (country_code, status);
