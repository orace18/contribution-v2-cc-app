CREATE TABLE circles (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    coach_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circles_coach FOREIGN KEY (coach_id) REFERENCES users(id),
    CONSTRAINT ck_circles_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_circles_coach_status ON circles (coach_id, status);

CREATE TABLE circle_members (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    business_activity VARCHAR(255),
    loan_status VARCHAR(32),
    repayment_progress NUMERIC(5,2),
    joined_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_members_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT fk_circle_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_circle_members_circle_user UNIQUE (circle_id, user_id),
    CONSTRAINT ck_circle_members_role CHECK (role IN ('COACH', 'MEMBER')),
    CONSTRAINT ck_circle_members_repayment_progress CHECK (
        repayment_progress IS NULL OR (repayment_progress >= 0 AND repayment_progress <= 100)
    )
);

CREATE INDEX idx_circle_members_user_id ON circle_members (user_id);
CREATE INDEX idx_circle_members_circle_id ON circle_members (circle_id);
CREATE UNIQUE INDEX uk_circle_members_one_coach ON circle_members(circle_id) WHERE role = 'COACH';

CREATE TABLE circle_activity (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    activity_type VARCHAR(32) NOT NULL,
    message VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_activity_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT fk_circle_activity_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_circle_activity_circle_created_at ON circle_activity (circle_id, created_at DESC);

CREATE OR REPLACE FUNCTION enforce_max_circle_members()
RETURNS trigger AS $$
DECLARE
    member_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO member_count
    FROM circle_members cm
    WHERE cm.circle_id = NEW.circle_id
      AND cm.id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid);

    IF member_count >= 5 THEN
        RAISE EXCEPTION 'Circle % already has maximum 5 members', NEW.circle_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_single_active_circle_per_user()
RETURNS trigger AS $$
DECLARE
    target_circle_status VARCHAR(16);
BEGIN
    SELECT c.status INTO target_circle_status
    FROM circles c
    WHERE c.id = NEW.circle_id;

    IF target_circle_status = 'ACTIVE' AND EXISTS (
        SELECT 1
        FROM circle_members cm
        JOIN circles c ON c.id = cm.circle_id
        WHERE cm.user_id = NEW.user_id
          AND c.status = 'ACTIVE'
          AND cm.id <> COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid)
    ) THEN
        RAISE EXCEPTION 'User % already belongs to an active circle', NEW.user_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_max_circle_members ON circle_members;
CREATE TRIGGER trg_enforce_max_circle_members
    BEFORE INSERT OR UPDATE OF circle_id
    ON circle_members
    FOR EACH ROW
EXECUTE FUNCTION enforce_max_circle_members();

DROP TRIGGER IF EXISTS trg_enforce_single_active_circle_per_user ON circle_members;
CREATE TRIGGER trg_enforce_single_active_circle_per_user
    BEFORE INSERT OR UPDATE OF user_id, circle_id
    ON circle_members
    FOR EACH ROW
EXECUTE FUNCTION enforce_single_active_circle_per_user();
