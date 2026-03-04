CREATE OR REPLACE FUNCTION prevent_accounting_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_mutation_accounting_events ON accounting_events;
CREATE TRIGGER trg_no_mutation_accounting_events
    BEFORE UPDATE OR DELETE
    ON accounting_events
    FOR EACH ROW
EXECUTE FUNCTION prevent_accounting_mutation();

DROP TRIGGER IF EXISTS trg_no_mutation_ledger_entries ON ledger_entries;
CREATE TRIGGER trg_no_mutation_ledger_entries
    BEFORE UPDATE OR DELETE
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION prevent_accounting_mutation();

INSERT INTO subscription_plans (id, code, name, country, currency, price, active, created_at, updated_at)
VALUES ('38c0c3d5-66cb-4d58-9615-63ba8d4b6dbc', 'BASIC_MONTHLY', 'Basic Monthly', 'CI', 'XOF', 5000.0000, TRUE, NOW(), NOW()),
       ('6079b4f5-5df8-4137-91f8-f4fd158c68e3', 'PRO_MONTHLY', 'Pro Monthly', 'CI', 'XOF', 12000.0000, TRUE, NOW(), NOW()),
       ('d6d4e34d-dd4e-4c99-8e0f-c1b0f3e6e938', 'BASIC_MONTHLY', 'Basic Monthly', 'SN', 'XOF', 5000.0000, TRUE, NOW(), NOW()),
       ('66d928e2-2dd2-4db0-a4b3-c0a2ab14f5cb', 'PRO_MONTHLY', 'Pro Monthly', 'SN', 'XOF', 12000.0000, TRUE, NOW(), NOW()),
       ('7c2967e0-006d-478a-97fc-2705be9eb9fa', 'BASIC_MONTHLY', 'Basic Monthly', 'US', 'USD', 9.9900, TRUE, NOW(), NOW()),
       ('8f03a798-fd3e-4f9b-8ecf-9df73088d5f6', 'PRO_MONTHLY', 'Pro Monthly', 'US', 'USD', 19.9900, TRUE, NOW(), NOW())
ON CONFLICT ON CONSTRAINT uk_subscription_plans_code_country_currency DO NOTHING;
