ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS project_name VARCHAR(160);

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS project_sector VARCHAR(120);

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS project_description VARCHAR(1000);

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS project_use_of_funds VARCHAR(500);
