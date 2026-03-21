ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE financial_partners fp
SET currency = c.currency
FROM countries c
WHERE fp.country_code = c.code
  AND fp.currency IS NULL;

UPDATE financial_partners
SET currency = 'USD'
WHERE currency IS NULL;

ALTER TABLE financial_partners
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(180);

ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS description VARCHAR(2000);
