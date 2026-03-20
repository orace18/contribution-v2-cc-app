-- V48: Add DRC (Congo Democratic Republic) as an operational country.
-- BJ and CI are already seeded in V43. CD completes the default trio: BJ, CD, CI.

INSERT INTO countries (id, code, name, currency, status, is_international_fallback, active, configured, fallback, created_at, updated_at)
VALUES (
    (
        SUBSTRING(MD5('country:CD'), 1, 8) || '-' ||
        SUBSTRING(MD5('country:CD'), 9, 4) || '-' ||
        SUBSTRING(MD5('country:CD'), 13, 4) || '-' ||
        SUBSTRING(MD5('country:CD'), 17, 4) || '-' ||
        SUBSTRING(MD5('country:CD'), 21, 12)
    )::UUID,
    'CD',
    'Congo (Dem. Rep.)',
    'CDF',
    'ACTIVE',
    FALSE,
    TRUE,
    FALSE,
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO UPDATE
SET name     = EXCLUDED.name,
    currency = EXCLUDED.currency,
    active   = TRUE,
    status   = 'ACTIVE',
    updated_at = NOW();
