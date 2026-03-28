-- V67: Enforce MLM rates as global-only — remove any rate fields from country MLM_LIMITS payloads
--
-- BUSINESS RULE (non-negotiable):
--   MLM rates (unilevelRates, binaryConfig and all legacy variants) are defined ONCE globally
--   in platform_config_versions WHERE config_type = 'GLOBAL_MLM_BASE_RATES'.
--   Country MLM_LIMITS payloads MUST contain only earning caps:
--     - maxDirectChildren
--     - weeklyCap
--     - weeklyCapsByTier
--   Any rate field in a country payload is silently ignored by the commission engine, but
--   its presence is misleading and could cause confusion. This migration permanently removes
--   such fields from all existing rows.
--
-- Fields removed from country_config_versions WHERE config_type = 'MLM_LIMITS':
--   unilevelRates, binaryConfig, binary, unilevel, generations,
--   g1 … g10 (flat legacy keys), baseRatesOverride, commissionRates,
--   overrideUnilevelRate, overrideBinaryRate, usePlatformFallback
--   (V63 already removed the last 5; this migration acts as a hard guard for any future drift)

UPDATE country_config_versions
SET payload = (
    CASE
        WHEN payload IS NULL OR btrim(payload) = '' THEN '{}'
        ELSE (
            payload::jsonb
            - 'unilevelRates'
            - 'binaryConfig'
            - 'binary'
            - 'unilevel'
            - 'generations'
            - 'g1'  - 'g2'  - 'g3'  - 'g4'  - 'g5'
            - 'g6'  - 'g7'  - 'g8'  - 'g9'  - 'g10'
            - 'baseRatesOverride'
            - 'commissionRates'
            - 'overrideUnilevelRate'
            - 'overrideBinaryRate'
            - 'usePlatformFallback'
        )
    END
)::text
WHERE config_type = 'MLM_LIMITS';
