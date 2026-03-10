ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS service_country_code VARCHAR(3);

ALTER TABLE subscription_plans
    ALTER COLUMN country TYPE VARCHAR(3);

ALTER TABLE admin_policies
    ALTER COLUMN country_code TYPE VARCHAR(3);

WITH fallback_country AS (
    SELECT code
    FROM countries
    WHERE fallback = TRUE
    ORDER BY code
    LIMIT 1
)
UPDATE account_holders ah
SET country_code = (
    SELECT code
    FROM fallback_country
)
WHERE ah.country_code IS NULL
   OR NOT EXISTS (
    SELECT 1
    FROM countries c
    WHERE c.code = UPPER(ah.country_code)
);

WITH fallback_country AS (
    SELECT code
    FROM countries
    WHERE fallback = TRUE
    ORDER BY code
    LIMIT 1
)
UPDATE account_holders ah
SET service_country_code = CASE
    WHEN EXISTS (
        SELECT 1
        FROM countries c
        WHERE c.code = UPPER(ah.country_code)
          AND c.active = TRUE
          AND c.configured = TRUE
    ) THEN UPPER(ah.country_code)
    ELSE (SELECT code FROM fallback_country)
END
WHERE ah.service_country_code IS NULL;

ALTER TABLE account_holders
    ALTER COLUMN service_country_code SET NOT NULL;

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS fk_account_holders_service_country_code;

ALTER TABLE account_holders
    ADD CONSTRAINT fk_account_holders_service_country_code
        FOREIGN KEY (service_country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_account_holders_service_country_code
    ON account_holders (service_country_code);

UPDATE circles c
SET country_code = ah.service_country_code
FROM account_holders ah
WHERE c.coach_id = ah.id;

UPDATE loans l
SET country_code = ah.service_country_code
FROM account_holders ah
WHERE l.account_holder_id = ah.id;

UPDATE capital_pools cp
SET country_code = c.country_code
FROM circles c
WHERE cp.circle_id = c.id;
