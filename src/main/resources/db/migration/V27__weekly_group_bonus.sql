CREATE TABLE IF NOT EXISTS mlm_leg_carryover (
    member_id UUID NOT NULL,
    leg_position INTEGER NOT NULL,
    volume NUMERIC(19,4) NOT NULL,
    week_id INTEGER NOT NULL,
    CONSTRAINT pk_mlm_leg_carryover PRIMARY KEY (member_id, leg_position, week_id),
    CONSTRAINT fk_mlm_leg_carryover_member
        FOREIGN KEY (member_id) REFERENCES account_holders(id),
    CONSTRAINT ck_mlm_leg_carryover_leg_position_positive
        CHECK (leg_position > 0),
    CONSTRAINT ck_mlm_leg_carryover_volume_non_negative
        CHECK (volume >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mlm_leg_carryover_member_id
    ON mlm_leg_carryover (member_id);

CREATE INDEX IF NOT EXISTS idx_mlm_leg_carryover_week_id
    ON mlm_leg_carryover (week_id);

CREATE INDEX IF NOT EXISTS idx_mlm_leg_carryover_leg_position
    ON mlm_leg_carryover (leg_position);

ALTER TABLE mlm_commissions
    ALTER COLUMN operation_id DROP NOT NULL;

ALTER TABLE mlm_commissions
    ADD COLUMN IF NOT EXISTS commission_type VARCHAR(32) NOT NULL DEFAULT 'SUBSCRIPTION',
    ADD COLUMN IF NOT EXISTS week_id INTEGER;

ALTER TABLE mlm_commissions
    DROP CONSTRAINT IF EXISTS ck_mlm_commissions_commission_type;

ALTER TABLE mlm_commissions
    ADD CONSTRAINT ck_mlm_commissions_commission_type
        CHECK (commission_type IN ('SUBSCRIPTION', 'GROUP_BONUS'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_mlm_commissions_member_type_week
    ON mlm_commissions (beneficiary_account_holder_id, commission_type, week_id);

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_type_week
    ON mlm_commissions (commission_type, week_id);

CREATE TABLE IF NOT EXISTS mlm_member_grades (
    member_id UUID PRIMARY KEY,
    grade VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mlm_member_grades_member
        FOREIGN KEY (member_id) REFERENCES account_holders(id),
    CONSTRAINT ck_mlm_member_grades_grade
        CHECK (grade IN ('CLASSIC', 'GOLD', 'DIAMOND'))
);

CREATE INDEX IF NOT EXISTS idx_mlm_member_grades_grade
    ON mlm_member_grades (grade);
