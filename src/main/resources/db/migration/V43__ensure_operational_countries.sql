WITH seed(code, name, currency) AS (
VALUES
    ('BJ', 'Benin', 'XOF'),
    ('CI', 'Cote d''Ivoire', 'XOF'),
    ('SN', 'Senegal', 'XOF'),
    ('GH', 'Ghana', 'GHS'),
    ('TG', 'Togo', 'XOF')
)
INSERT INTO countries (id, code, name, currency, status, is_international_fallback, active, configured, fallback, created_at, updated_at)
SELECT
    (
        SUBSTRING(MD5('country:' || s.code), 1, 8) || '-' ||
        SUBSTRING(MD5('country:' || s.code), 9, 4) || '-' ||
        SUBSTRING(MD5('country:' || s.code), 13, 4) || '-' ||
        SUBSTRING(MD5('country:' || s.code), 17, 4) || '-' ||
        SUBSTRING(MD5('country:' || s.code), 21, 12)
    )::UUID,
    s.code,
    s.name,
    s.currency,
    'ACTIVE',
    FALSE,
    TRUE,
    FALSE,
    FALSE,
    NOW(),
    NOW()
FROM seed s
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    currency = EXCLUDED.currency,
    active = TRUE,
    status = 'ACTIVE',
    updated_at = NOW();
