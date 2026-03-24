UPDATE country_config_versions
SET payload = (
    CASE
        WHEN payload IS NULL OR btrim(payload) = '' THEN '{}'::jsonb
        ELSE payload::jsonb
    END
    - 'baseRatesOverride'
    - 'commissionRates'
    - 'overrideUnilevelRate'
    - 'overrideBinaryRate'
    - 'usePlatformFallback'
)::text
WHERE config_type = 'MLM_LIMITS';

WITH normalized_global_mlm AS (
    SELECT
        pcv.id,
        jsonb_build_object(
            'unilevelRates', jsonb_build_object(
                'g1', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g1}', '')::numeric, NULLIF(payload_json #>> '{generations,g1}', '')::numeric, NULLIF(payload_json ->> 'g1', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g2', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g2}', '')::numeric, NULLIF(payload_json #>> '{generations,g2}', '')::numeric, NULLIF(payload_json ->> 'g2', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g3', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g3}', '')::numeric, NULLIF(payload_json #>> '{generations,g3}', '')::numeric, NULLIF(payload_json ->> 'g3', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g4', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g4}', '')::numeric, NULLIF(payload_json #>> '{generations,g4}', '')::numeric, NULLIF(payload_json ->> 'g4', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g5', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g5}', '')::numeric, NULLIF(payload_json #>> '{generations,g5}', '')::numeric, NULLIF(payload_json ->> 'g5', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g6', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g6}', '')::numeric, NULLIF(payload_json #>> '{generations,g6}', '')::numeric, NULLIF(payload_json ->> 'g6', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g7', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g7}', '')::numeric, NULLIF(payload_json #>> '{generations,g7}', '')::numeric, NULLIF(payload_json ->> 'g7', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g8', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g8}', '')::numeric, NULLIF(payload_json #>> '{generations,g8}', '')::numeric, NULLIF(payload_json ->> 'g8', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g9', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g9}', '')::numeric, NULLIF(payload_json #>> '{generations,g9}', '')::numeric, NULLIF(payload_json ->> 'g9', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0),
                'g10', COALESCE(NULLIF(payload_json #>> '{unilevelRates,g10}', '')::numeric, NULLIF(payload_json #>> '{generations,g10}', '')::numeric, NULLIF(payload_json ->> 'g10', '')::numeric, NULLIF(payload_json ->> 'unilevel', '')::numeric, 0)
            ),
            'binaryConfig', jsonb_build_object(
                'matchingRule', COALESCE(NULLIF(payload_json #>> '{binaryConfig,matchingRule}', ''), 'WEAKER_LEG'),
                'percentage', COALESCE(NULLIF(payload_json #>> '{binaryConfig,percentage}', '')::numeric, NULLIF(payload_json ->> 'binary', '')::numeric, 0)
            )
        ) AS normalized_payload
    FROM (
        SELECT
            id,
            CASE
                WHEN payload IS NULL OR btrim(payload) = '' THEN '{}'::jsonb
                ELSE payload::jsonb
            END AS payload_json
        FROM platform_config_versions
        WHERE config_type = 'GLOBAL_MLM_BASE_RATES'
    ) pcv
)
UPDATE platform_config_versions pcv
SET payload = normalized_global_mlm.normalized_payload::text
FROM normalized_global_mlm
WHERE pcv.id = normalized_global_mlm.id;
