DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'circle_members'
          AND column_name = 'user_id'
    ) THEN
        ALTER TABLE circle_members RENAME COLUMN user_id TO account_holder_id;
    END IF;
END $$;

ALTER TABLE circles
    DROP CONSTRAINT IF EXISTS fk_circles_coach;
ALTER TABLE circles
    DROP CONSTRAINT IF EXISTS fk_circles_coach_account_holder;
ALTER TABLE circles
    ADD CONSTRAINT fk_circles_coach_account_holder
        FOREIGN KEY (coach_id) REFERENCES account_holders(id);

ALTER TABLE circle_members
    ADD COLUMN IF NOT EXISTS is_financial_member BOOLEAN;

UPDATE circle_members
SET is_financial_member = CASE
    WHEN role = 'MEMBER' THEN TRUE
    ELSE FALSE
END
WHERE is_financial_member IS NULL;

ALTER TABLE circle_members
    ALTER COLUMN is_financial_member SET NOT NULL;
ALTER TABLE circle_members
    ALTER COLUMN is_financial_member SET DEFAULT TRUE;

ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS fk_circle_members_user;
ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS fk_circle_members_account_holder;
ALTER TABLE circle_members
    ADD CONSTRAINT fk_circle_members_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id);

DROP INDEX IF EXISTS idx_circle_members_user_id;
CREATE INDEX IF NOT EXISTS idx_circle_members_account_holder_id
    ON circle_members (account_holder_id);

ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS uk_circle_members_circle_user;
ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS uk_circle_members_circle_account_holder;
ALTER TABLE circle_members
    ADD CONSTRAINT uk_circle_members_circle_account_holder
        UNIQUE (circle_id, account_holder_id);

ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS ck_circle_members_role;
ALTER TABLE circle_members
    ADD CONSTRAINT ck_circle_members_role
        CHECK (role IN ('MEMBER', 'COACH', 'ADVISOR_G1', 'ADVISOR_G2'));

ALTER TABLE circle_members
    DROP CONSTRAINT IF EXISTS ck_circle_members_financial_member_role;
ALTER TABLE circle_members
    ADD CONSTRAINT ck_circle_members_financial_member_role
        CHECK (
            (is_financial_member AND role = 'MEMBER')
            OR
            ((NOT is_financial_member) AND role IN ('COACH', 'ADVISOR_G1', 'ADVISOR_G2'))
        );

DROP INDEX IF EXISTS uk_circle_members_one_coach;
CREATE UNIQUE INDEX IF NOT EXISTS uk_circle_members_one_coach
    ON circle_members (circle_id)
    WHERE role = 'COACH';

CREATE OR REPLACE FUNCTION enforce_max_circle_members()
RETURNS trigger AS $$
DECLARE
    member_count INTEGER;
BEGIN
    IF NEW.is_financial_member THEN
        SELECT COUNT(*) INTO member_count
        FROM circle_members cm
        WHERE cm.circle_id = NEW.circle_id
          AND cm.is_financial_member = TRUE
          AND cm.id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid);

        IF member_count >= 5 THEN
            RAISE EXCEPTION 'Circle % already has maximum 5 financial members', NEW.circle_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_single_active_circle_per_user()
RETURNS trigger AS $$
DECLARE
    target_circle_status VARCHAR(16);
BEGIN
    IF NOT NEW.is_financial_member THEN
        RETURN NEW;
    END IF;

    SELECT c.status INTO target_circle_status
    FROM circles c
    WHERE c.id = NEW.circle_id;

    IF target_circle_status = 'ACTIVE' AND EXISTS (
        SELECT 1
        FROM circle_members cm
        JOIN circles c ON c.id = cm.circle_id
        WHERE cm.account_holder_id = NEW.account_holder_id
          AND cm.is_financial_member = TRUE
          AND c.status = 'ACTIVE'
          AND cm.id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid)
    ) THEN
        RAISE EXCEPTION 'Account holder % already belongs to an active circle as financial member', NEW.account_holder_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_max_circle_members ON circle_members;
CREATE TRIGGER trg_enforce_max_circle_members
    BEFORE INSERT OR UPDATE OF circle_id, is_financial_member
    ON circle_members
    FOR EACH ROW
EXECUTE FUNCTION enforce_max_circle_members();

DROP TRIGGER IF EXISTS trg_enforce_single_active_circle_per_user ON circle_members;
CREATE TRIGGER trg_enforce_single_active_circle_per_user
    BEFORE INSERT OR UPDATE OF account_holder_id, circle_id, is_financial_member
    ON circle_members
    FOR EACH ROW
EXECUTE FUNCTION enforce_single_active_circle_per_user();
