ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS supported_products VARCHAR(64);

UPDATE financial_partners
SET supported_products = 'CREDIT'
WHERE supported_products IS NULL
   OR BTRIM(supported_products) = '';

ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS api_integration_type VARCHAR(16);

UPDATE financial_partners
SET api_integration_type = CASE
    WHEN api_endpoint IS NOT NULL AND BTRIM(api_endpoint) <> '' THEN 'API'
    ELSE 'MANUAL'
END
WHERE api_integration_type IS NULL
   OR BTRIM(api_integration_type) = '';

ALTER TABLE financial_partners
    ADD COLUMN IF NOT EXISTS settlement_account VARCHAR(64);

UPDATE financial_partners fp
SET settlement_account = cca.account_code
FROM country_chart_accounts cca
WHERE fp.settlement_account IS NULL
  AND cca.country_code = fp.country_code
  AND cca.system_tag = 'PSP_CLEARING'
  AND cca.status = 'ACTIVE';

ALTER TABLE financial_partners
    DROP CONSTRAINT IF EXISTS ck_financial_partners_type;

ALTER TABLE financial_partners
    ADD CONSTRAINT ck_financial_partners_type
        CHECK (type IN ('IMF', 'BANK', 'FINTECH', 'FUND'));

ALTER TABLE financial_partners
    DROP CONSTRAINT IF EXISTS ck_financial_partners_api_integration_type;

ALTER TABLE financial_partners
    ADD CONSTRAINT ck_financial_partners_api_integration_type
        CHECK (api_integration_type IN ('MANUAL', 'API'));

ALTER TABLE financing_offers
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(3);

UPDATE financing_offers fo
SET country_code = fp.country_code
FROM financial_partners fp
WHERE fo.country_code IS NULL
  AND fp.id = fo.partner_id;

ALTER TABLE financing_offers
    ADD COLUMN IF NOT EXISTS fees_json TEXT;

UPDATE financing_offers
SET fees_json = '{}'
WHERE fees_json IS NULL;

ALTER TABLE financing_offers
    ADD COLUMN IF NOT EXISTS eligibility_rules_json TEXT;

UPDATE financing_offers
SET eligibility_rules_json = '{}'
WHERE eligibility_rules_json IS NULL;

ALTER TABLE financing_offers
    ADD COLUMN IF NOT EXISTS required_documents_json TEXT;

UPDATE financing_offers
SET required_documents_json = '{}'
WHERE required_documents_json IS NULL;

ALTER TABLE financing_offers
    ADD COLUMN IF NOT EXISTS scoring_rules_json TEXT;

UPDATE financing_offers
SET scoring_rules_json = '{}'
WHERE scoring_rules_json IS NULL;

UPDATE financing_offers
SET loan_type = 'CREDIT'
WHERE loan_type IS NULL
   OR UPPER(BTRIM(loan_type)) NOT IN ('SAVINGS', 'CREDIT');

ALTER TABLE financing_offers
    ADD CONSTRAINT fk_financing_offers_country
        FOREIGN KEY (country_code) REFERENCES countries(code);

CREATE INDEX IF NOT EXISTS idx_financing_offers_country_status
    ON financing_offers (country_code, status);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS decision_note VARCHAR(1000);
