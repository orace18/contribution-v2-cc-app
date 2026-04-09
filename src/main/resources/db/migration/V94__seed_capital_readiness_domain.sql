ALTER TABLE circle_activity
    DROP CONSTRAINT IF EXISTS ck_circle_activity_type;

ALTER TABLE circle_activity
    ADD CONSTRAINT ck_circle_activity_type CHECK (
        activity_type IN (
            'MEMBER_JOINED',
            'MEMBER_REMOVED',
            'PROJECT_SUBMITTED',
            'TRAINING_COMPLETED',
            'CONTRIBUTION_PAID',
            'LOAN_GRANTED',
            'LOAN_REPAID',
            'MEETING_COMPLETED',
            'COACH_ASSIGNED'
        )
    );

CREATE TABLE IF NOT EXISTS circle_coach_assignments (
    circle_id UUID PRIMARY KEY,
    coach_account_holder_id UUID NOT NULL,
    assigned_by_account_holder_id UUID,
    source VARCHAR(32) NOT NULL,
    note VARCHAR(500),
    assigned_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_coach_assignments_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT fk_circle_coach_assignments_coach
        FOREIGN KEY (coach_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_circle_coach_assignments_assigned_by
        FOREIGN KEY (assigned_by_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_circle_coach_assignments_source
        CHECK (source IN ('MANUAL', 'NETWORK_INHERITED', 'ADMIN_OVERRIDE'))
);

CREATE TABLE IF NOT EXISTS circle_meetings (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    created_by_account_holder_id UUID NOT NULL,
    meeting_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    participant_count INTEGER NOT NULL DEFAULT 0,
    attendance_count INTEGER NOT NULL DEFAULT 0,
    quorum_reached BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_meetings_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT fk_circle_meetings_created_by
        FOREIGN KEY (created_by_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_circle_meetings_status
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT ck_circle_meetings_participant_count
        CHECK (participant_count >= 0),
    CONSTRAINT ck_circle_meetings_attendance_count
        CHECK (attendance_count >= 0 AND attendance_count <= participant_count)
);

CREATE INDEX IF NOT EXISTS idx_circle_meetings_circle_status_meeting
    ON circle_meetings (circle_id, status, meeting_at DESC);

CREATE TABLE IF NOT EXISTS circle_meeting_attendance (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_meeting_attendance_meeting
        FOREIGN KEY (meeting_id) REFERENCES circle_meetings(id) ON DELETE CASCADE,
    CONSTRAINT fk_circle_meeting_attendance_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_circle_meeting_attendance_meeting_member
        UNIQUE (meeting_id, account_holder_id),
    CONSTRAINT ck_circle_meeting_attendance_status
        CHECK (status IN ('PRESENT', 'ABSENT'))
);

CREATE INDEX IF NOT EXISTS idx_circle_meeting_attendance_meeting
    ON circle_meeting_attendance (meeting_id);

CREATE TABLE IF NOT EXISTS circle_trust_scores (
    circle_id UUID PRIMARY KEY,
    members_count INTEGER NOT NULL DEFAULT 0,
    financially_ready_members_count INTEGER NOT NULL DEFAULT 0,
    completed_meetings_count INTEGER NOT NULL DEFAULT 0,
    recent_activity_count INTEGER NOT NULL DEFAULT 0,
    coach_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    membership_compliance_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    circle_size_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    meetings_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    activity_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    coach_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    global_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_trust_scores_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT ck_circle_trust_scores_members_count
        CHECK (members_count >= 0),
    CONSTRAINT ck_circle_trust_scores_financially_ready_count
        CHECK (financially_ready_members_count >= 0 AND financially_ready_members_count <= members_count),
    CONSTRAINT ck_circle_trust_scores_completed_meetings_count
        CHECK (completed_meetings_count >= 0),
    CONSTRAINT ck_circle_trust_scores_recent_activity_count
        CHECK (recent_activity_count >= 0),
    CONSTRAINT ck_circle_trust_scores_membership
        CHECK (membership_compliance_score >= 0 AND membership_compliance_score <= 100),
    CONSTRAINT ck_circle_trust_scores_size
        CHECK (circle_size_score >= 0 AND circle_size_score <= 100),
    CONSTRAINT ck_circle_trust_scores_meetings
        CHECK (meetings_score >= 0 AND meetings_score <= 100),
    CONSTRAINT ck_circle_trust_scores_activity
        CHECK (activity_score >= 0 AND activity_score <= 100),
    CONSTRAINT ck_circle_trust_scores_coach
        CHECK (coach_score >= 0 AND coach_score <= 100),
    CONSTRAINT ck_circle_trust_scores_global
        CHECK (global_score >= 0 AND global_score <= 100)
);

CREATE TABLE IF NOT EXISTS seed_capital_eligibility (
    circle_id UUID PRIMARY KEY,
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    state VARCHAR(32) NOT NULL,
    trust_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    valid_circle_size BOOLEAN NOT NULL DEFAULT FALSE,
    membership_compliant BOOLEAN NOT NULL DEFAULT FALSE,
    meetings_requirement_met BOOLEAN NOT NULL DEFAULT FALSE,
    coach_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    trust_score_threshold_met BOOLEAN NOT NULL DEFAULT FALSE,
    blocking_reasons TEXT,
    satisfied_criteria TEXT,
    pending_criteria TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_seed_capital_eligibility_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT ck_seed_capital_eligibility_state
        CHECK (state IN ('BLOCKED', 'READY_FOR_FUTURE_GATES', 'ELIGIBLE')),
    CONSTRAINT ck_seed_capital_eligibility_trust_score
        CHECK (trust_score >= 0 AND trust_score <= 100)
);
