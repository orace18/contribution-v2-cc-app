ALTER TABLE subscription_plans
    ADD COLUMN IF NOT EXISTS grade VARCHAR(16),
    ADD COLUMN IF NOT EXISTS duration_months INTEGER;

UPDATE subscription_plans
SET grade = CASE
    WHEN UPPER(COALESCE(code, '')) LIKE '%FREE%' OR UPPER(COALESCE(name, '')) LIKE '%FREE%' THEN 'FREE'
    WHEN UPPER(COALESCE(code, '')) LIKE '%DIAMOND%' OR UPPER(COALESCE(name, '')) LIKE '%DIAMOND%' THEN 'DIAMOND'
    WHEN UPPER(COALESCE(code, '')) LIKE '%GOLD%' OR UPPER(COALESCE(name, '')) LIKE '%GOLD%'
        OR UPPER(COALESCE(code, '')) LIKE '%PRO%' OR UPPER(COALESCE(name, '')) LIKE '%PRO%' THEN 'GOLD'
    ELSE 'CLASSIC'
END
WHERE grade IS NULL;

UPDATE subscription_plans
SET duration_months = CASE
    WHEN UPPER(COALESCE(code, '')) LIKE '%12M%' OR UPPER(COALESCE(name, '')) LIKE '%12 MONTH%'
        OR UPPER(COALESCE(name, '')) LIKE '%ANNUAL%' OR UPPER(COALESCE(name, '')) LIKE '%YEAR%' THEN 12
    WHEN UPPER(COALESCE(code, '')) LIKE '%3M%' OR UPPER(COALESCE(name, '')) LIKE '%3 MONTH%'
        OR UPPER(COALESCE(name, '')) LIKE '%QUARTER%' THEN 3
    ELSE 1
END
WHERE duration_months IS NULL;

ALTER TABLE subscription_plans
    ALTER COLUMN grade SET NOT NULL,
    ALTER COLUMN duration_months SET NOT NULL;

ALTER TABLE subscription_plans
    DROP CONSTRAINT IF EXISTS ck_subscription_plans_grade;

ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_grade
        CHECK (grade IN ('CLASSIC', 'GOLD', 'DIAMOND', 'FREE'));

ALTER TABLE subscription_plans
    DROP CONSTRAINT IF EXISTS ck_subscription_plans_duration_months_positive;

ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_duration_months_positive
        CHECK (duration_months > 0);

ALTER TABLE subscription_plans
    DROP CONSTRAINT IF EXISTS uk_subscription_plans_country_grade_duration_currency;

ALTER TABLE subscription_plans
    ADD CONSTRAINT uk_subscription_plans_country_grade_duration_currency
        UNIQUE (country, grade, duration_months, currency);

CREATE INDEX IF NOT EXISTS idx_subscription_plans_country_grade_duration
    ON subscription_plans (country, grade, duration_months);

ALTER TABLE memberships
    ADD COLUMN IF NOT EXISTS plan_id UUID,
    ADD COLUMN IF NOT EXISTS plan_grade VARCHAR(16),
    ADD COLUMN IF NOT EXISTS duration_months INTEGER;

UPDATE memberships m
SET plan_id = sp.id,
    plan_grade = sp.grade,
    duration_months = sp.duration_months
FROM subscription_plans sp
WHERE m.plan_id IS NULL
  AND sp.code = m.plan_code
  AND sp.country = m.country_code
  AND sp.currency = m.currency;

ALTER TABLE memberships
    DROP CONSTRAINT IF EXISTS fk_memberships_plan;

ALTER TABLE memberships
    ADD CONSTRAINT fk_memberships_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id);

ALTER TABLE memberships
    DROP CONSTRAINT IF EXISTS ck_memberships_plan_grade;

ALTER TABLE memberships
    ADD CONSTRAINT ck_memberships_plan_grade
        CHECK (plan_grade IS NULL OR plan_grade IN ('CLASSIC', 'GOLD', 'DIAMOND', 'FREE'));

CREATE INDEX IF NOT EXISTS idx_memberships_plan_id
    ON memberships (plan_id);

ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS plan_id UUID;

UPDATE operations o
SET plan_id = m.plan_id
FROM memberships m
WHERE o.plan_id IS NULL
  AND m.operation_id = o.id;

ALTER TABLE operations
    DROP CONSTRAINT IF EXISTS fk_operations_plan;

ALTER TABLE operations
    ADD CONSTRAINT fk_operations_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id);

CREATE INDEX IF NOT EXISTS idx_operations_plan_id
    ON operations (plan_id);

ALTER TABLE mlm_commissions
    ADD COLUMN IF NOT EXISTS generation INTEGER;

UPDATE mlm_commissions
SET generation = CASE
    WHEN commission_type = 'GROUP_BONUS' THEN 0
    ELSE 1
END
WHERE generation IS NULL;

ALTER TABLE mlm_commissions
    ALTER COLUMN generation SET NOT NULL;

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_generation_non_negative;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_generation_non_negative
        CHECK (generation >= 0);

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS uk_mlm_commissions_operation;

DROP INDEX IF EXISTS uk_mlm_commissions_operation;
DROP INDEX IF EXISTS uk_mlm_commissions_member_type_week;
DROP INDEX IF EXISTS uk_mlm_commissions_member_type_week_generation;
DROP INDEX IF EXISTS uk_mlm_commissions_group_bonus_member_week;
DROP INDEX IF EXISTS uk_mlm_commissions_operation_member_generation;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mlm_commissions_operation_member_generation
    ON mlm_commissions (operation_id, beneficiary_account_holder_id, generation)
    WHERE operation_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mlm_commissions_group_bonus_member_week
    ON mlm_commissions (beneficiary_account_holder_id, commission_type, week_id)
    WHERE commission_type = 'GROUP_BONUS';

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_operation_generation
    ON mlm_commissions (operation_id, generation);
