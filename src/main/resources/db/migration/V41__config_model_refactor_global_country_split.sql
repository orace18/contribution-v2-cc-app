DROP TABLE IF EXISTS tmp_flyway_v41_country_final;

CREATE TEMP TABLE tmp_flyway_v41_country_final AS
WITH RECURSIVE country_mapped AS (
    SELECT
        ccv.id AS legacy_id,
        ccv.country_code,
        CASE ccv.config_type
            WHEN 'ACCOUNTING_CONFIG' THEN 'ACCOUNTING'
            WHEN 'MLM_CONFIG' THEN 'MLM_LIMITS'
            WHEN 'MEMBERSHIP_CONFIG' THEN 'MEMBERSHIP'
            WHEN 'FINANCING_CONFIG' THEN 'FINANCING'
            WHEN 'PAYMENT_CONFIG' THEN 'PAYMENT'
            WHEN 'RISK_CONFIG' THEN 'GUARANTEE'
            WHEN 'CIRCLE_CONFIG' THEN 'GENERAL'
            WHEN 'KYC_CONFIG' THEN 'GENERAL'
            WHEN 'FEATURE_FLAGS' THEN 'GENERAL'
            ELSE ccv.config_type
        END AS target_type,
        ccv.effective_date,
        ccv.created_by_admin_user_id,
        ccv.created_at,
        CASE ccv.config_type
            WHEN 'ACCOUNTING_CONFIG' THEN
                CASE
                    WHEN payload_json ? 'accounts' THEN payload_json
                    WHEN payload_json ? 'preallocationAccounts' THEN jsonb_build_object('accounts', payload_json -> 'preallocationAccounts')
                    ELSE payload_json
                END
            WHEN 'MLM_CONFIG' THEN
                CASE
                    WHEN payload_json ? 'weeklyCap' THEN payload_json
                    ELSE payload_json || jsonb_build_object(
                        'weeklyCap',
                        CASE
                            WHEN payload_json ? 'caps' AND jsonb_typeof(payload_json -> 'caps') = 'object'
                                 AND (payload_json -> 'caps') ? 'monthly'
                                THEN ((payload_json -> 'caps' ->> 'monthly')::numeric / 4.0)
                            ELSE NULL
                        END
                    )
                END
            WHEN 'RISK_CONFIG' THEN
                CASE
                    WHEN payload_json ? 'globalMaxExposure' THEN payload_json
                    WHEN payload_json ? 'maxExposure' THEN payload_json || jsonb_build_object('globalMaxExposure', payload_json -> 'maxExposure')
                    ELSE payload_json
                END
            WHEN 'CIRCLE_CONFIG' THEN jsonb_build_object('circle', payload_json)
            WHEN 'FEATURE_FLAGS' THEN jsonb_build_object('features', payload_json)
            WHEN 'KYC_CONFIG' THEN jsonb_build_object('kyc', payload_json)
            ELSE payload_json
        END AS patch_payload
    FROM (
        SELECT
            id,
            country_code,
            config_type,
            effective_date,
            created_by_admin_user_id,
            created_at,
            CASE
                WHEN payload IS NULL OR btrim(payload) = '' THEN '{}'::jsonb
                ELSE payload::jsonb
            END AS payload_json
        FROM country_config_versions
    ) ccv
),
country_ordered AS (
    SELECT
        legacy_id,
        country_code,
        target_type,
        effective_date,
        created_by_admin_user_id,
        created_at,
        patch_payload,
        ROW_NUMBER() OVER (
            PARTITION BY country_code, target_type
            ORDER BY effective_date ASC, created_at ASC, legacy_id ASC
        ) AS rn
    FROM country_mapped
),
country_merged AS (
    SELECT
        legacy_id,
        country_code,
        target_type,
        effective_date,
        created_by_admin_user_id,
        created_at,
        rn,
        patch_payload AS merged_payload
    FROM country_ordered
    WHERE rn = 1

    UNION ALL

    SELECT
        nxt.legacy_id,
        nxt.country_code,
        nxt.target_type,
        nxt.effective_date,
        nxt.created_by_admin_user_id,
        nxt.created_at,
        nxt.rn,
        CASE
            WHEN nxt.target_type = 'GENERAL' THEN prev.merged_payload || nxt.patch_payload
            ELSE nxt.patch_payload
        END AS merged_payload
    FROM country_merged prev
    JOIN country_ordered nxt
      ON nxt.country_code = prev.country_code
     AND nxt.target_type = prev.target_type
     AND nxt.rn = prev.rn + 1
),
country_final AS (
    SELECT
        (
            SUBSTRING(MD5(CONCAT('country-config-v41:', legacy_id::text)), 1, 8) || '-' ||
            SUBSTRING(MD5(CONCAT('country-config-v41:', legacy_id::text)), 9, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('country-config-v41:', legacy_id::text)), 13, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('country-config-v41:', legacy_id::text)), 17, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('country-config-v41:', legacy_id::text)), 21, 12)
        )::UUID AS id,
        country_code,
        target_type AS config_type,
        rn AS version,
        effective_date,
        merged_payload::text AS payload,
        created_by_admin_user_id,
        created_at
    FROM country_merged
)
SELECT
    id,
    country_code,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
FROM country_final;

DELETE FROM country_config_versions;

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
    id,
    country_code,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
FROM tmp_flyway_v41_country_final;

DROP TABLE IF EXISTS tmp_flyway_v41_platform_final;

CREATE TEMP TABLE tmp_flyway_v41_platform_final AS
WITH RECURSIVE platform_mapped AS (
    SELECT
        pcv.id AS legacy_id,
        CASE pcv.config_type
            WHEN 'GLOBAL_MLM_FALLBACK' THEN 'GLOBAL_MLM_BASE_RATES'
            WHEN 'GLOBAL_FEATURE_FLAGS' THEN 'GLOBAL_GENERAL'
            WHEN 'SYSTEM_LIMITS' THEN 'GLOBAL_GENERAL'
            ELSE pcv.config_type
        END AS target_type,
        pcv.effective_date,
        pcv.created_by_admin_user_id,
        pcv.created_at,
        CASE pcv.config_type
            WHEN 'GLOBAL_MLM_FALLBACK' THEN
                CASE
                    WHEN payload_json ? 'unilevel' AND payload_json ? 'binary' THEN payload_json
                    ELSE jsonb_build_object(
                        'unilevel', 0,
                        'binary', 0,
                        'legacy', payload_json
                    )
                END
            WHEN 'GLOBAL_FEATURE_FLAGS' THEN jsonb_build_object('features', payload_json)
            WHEN 'SYSTEM_LIMITS' THEN jsonb_build_object('limits', payload_json)
            ELSE payload_json
        END AS patch_payload
    FROM (
        SELECT
            id,
            config_type,
            effective_date,
            created_by_admin_user_id,
            created_at,
            CASE
                WHEN payload IS NULL OR btrim(payload) = '' THEN '{}'::jsonb
                ELSE payload::jsonb
            END AS payload_json
        FROM platform_config_versions
    ) pcv
),
platform_ordered AS (
    SELECT
        legacy_id,
        target_type,
        effective_date,
        created_by_admin_user_id,
        created_at,
        patch_payload,
        ROW_NUMBER() OVER (
            PARTITION BY target_type
            ORDER BY effective_date ASC, created_at ASC, legacy_id ASC
        ) AS rn
    FROM platform_mapped
),
platform_merged AS (
    SELECT
        legacy_id,
        target_type,
        effective_date,
        created_by_admin_user_id,
        created_at,
        rn,
        patch_payload AS merged_payload
    FROM platform_ordered
    WHERE rn = 1

    UNION ALL

    SELECT
        nxt.legacy_id,
        nxt.target_type,
        nxt.effective_date,
        nxt.created_by_admin_user_id,
        nxt.created_at,
        nxt.rn,
        CASE
            WHEN nxt.target_type = 'GLOBAL_GENERAL' THEN prev.merged_payload || nxt.patch_payload
            ELSE nxt.patch_payload
        END AS merged_payload
    FROM platform_merged prev
    JOIN platform_ordered nxt
      ON nxt.target_type = prev.target_type
     AND nxt.rn = prev.rn + 1
),
platform_final AS (
    SELECT
        (
            SUBSTRING(MD5(CONCAT('platform-config-v41:', legacy_id::text)), 1, 8) || '-' ||
            SUBSTRING(MD5(CONCAT('platform-config-v41:', legacy_id::text)), 9, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('platform-config-v41:', legacy_id::text)), 13, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('platform-config-v41:', legacy_id::text)), 17, 4) || '-' ||
            SUBSTRING(MD5(CONCAT('platform-config-v41:', legacy_id::text)), 21, 12)
        )::UUID AS id,
        target_type AS config_type,
        rn AS version,
        effective_date,
        merged_payload::text AS payload,
        created_by_admin_user_id,
        created_at
    FROM platform_merged
)
SELECT
    id,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
FROM platform_final;

DELETE FROM platform_config_versions;

INSERT INTO platform_config_versions (
    id,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
)
SELECT
    id,
    config_type,
    version,
    effective_date,
    payload,
    created_by_admin_user_id,
    created_at
FROM tmp_flyway_v41_platform_final;

DROP TABLE IF EXISTS tmp_flyway_v41_country_final;
DROP TABLE IF EXISTS tmp_flyway_v41_platform_final;
