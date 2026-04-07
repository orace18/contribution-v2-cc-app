-- Add category and image_url to shop_products
ALTER TABLE shop_products
    ADD COLUMN IF NOT EXISTS category   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS image_url  VARCHAR(512);

-- Seed 3 demo products using the first available account_holder as merchant.
-- If no account_holder exists the INSERT is a no-op (SELECT returns 0 rows).
INSERT INTO shop_products
    (id, merchant_id, name, description, category, price, currency,
     commission_percent, cv_value, active, image_url)
SELECT
    gen_random_uuid(),
    ah.id,
    'T-shirt CC Benin',
    'Edition Club Chretien Benin. Confortable, simple et ideale pour soutenir le club.',
    'T-shirt',
    2500, 'XOF', 10.00, 2.0000, TRUE, NULL
FROM account_holders ah
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO shop_products
    (id, merchant_id, name, description, category, price, currency,
     commission_percent, cv_value, active, image_url)
SELECT
    gen_random_uuid(),
    ah.id,
    'Casquette CC',
    'Casquette officielle du club, sobre et facile a porter au quotidien.',
    'Casquette',
    2500, 'XOF', 10.00, 2.0000, TRUE, NULL
FROM account_holders ah
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO shop_products
    (id, merchant_id, name, description, category, price, currency,
     commission_percent, cv_value, active, image_url)
SELECT
    gen_random_uuid(),
    ah.id,
    'Sac CC',
    'Sac pratique et resistant pour afficher ton identite chretienne avec style.',
    'Sac',
    2500, 'XOF', 10.00, 2.0000, TRUE, NULL
FROM account_holders ah
LIMIT 1
ON CONFLICT DO NOTHING;
