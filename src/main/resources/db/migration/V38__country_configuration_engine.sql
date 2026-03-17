CREATE TABLE IF NOT EXISTS country_config_versions (
    id UUID PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    config_type VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    created_by_admin_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_country_config_versions_country_type_version
        UNIQUE (country_code, config_type, version)
);

CREATE INDEX IF NOT EXISTS idx_country_config_versions_country_type_effective
    ON country_config_versions (country_code, config_type, effective_date DESC, version DESC);

CREATE INDEX IF NOT EXISTS idx_country_config_versions_country_effective
    ON country_config_versions (country_code, effective_date DESC);

WITH ranked_legacy_policies AS (
    SELECT
        (
            SUBSTRING(MD5(CONCAT('legacy-country-config:', p.id::text)), 1, 8) || '-' ||
            SUBSTRING(MD5(CONCAT('legacy-country-config:', p.id::text)), 9, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('legacy-country-config:', p.id::text)), 13, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('legacy-country-config:', p.id::text)), 17, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('legacy-country-config:', p.id::text)), 21, 12)
        )::UUID AS generated_id,
        UPPER(p.country_code) AS country_code,
        CASE
            WHEN p.policy_type = 'CREDIT_POLICY' THEN 'RISK_CONFIG'
            WHEN p.policy_type = 'SEED_CAPITAL_POLICY' THEN 'FINANCING_CONFIG'
        END AS config_type,
        ROW_NUMBER() OVER (
            PARTITION BY UPPER(p.country_code),
            CASE
                WHEN p.policy_type = 'CREDIT_POLICY' THEN 'RISK_CONFIG'
                WHEN p.policy_type = 'SEED_CAPITAL_POLICY' THEN 'FINANCING_CONFIG'
            END
            ORDER BY p.created_at ASC
        ) AS version,
        p.created_at AS effective_date,
        p.payload AS payload,
        p.created_at AS created_at
    FROM admin_policies p
    WHERE p.country_code IS NOT NULL
      AND p.policy_type IN ('CREDIT_POLICY', 'SEED_CAPITAL_POLICY')
)
INSERT INTO country_config_versions (
    id,
    country_code,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
)
SELECT
    generated_id,
    country_code,
    config_type,
    version,
    effective_date,
    payload,
    NULL,
    created_at
FROM ranked_legacy_policies
ON CONFLICT ON CONSTRAINT uk_country_config_versions_country_type_version DO NOTHING;
