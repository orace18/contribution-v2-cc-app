ALTER TABLE circle_trust_scores
    ADD COLUMN IF NOT EXISTS network_signal_score NUMERIC(5,2) NOT NULL DEFAULT 0;

ALTER TABLE circle_trust_scores
    ADD COLUMN IF NOT EXISTS network_bonus_score NUMERIC(5,2) NOT NULL DEFAULT 0;

ALTER TABLE circle_trust_scores
    ADD COLUMN IF NOT EXISTS network_advisors_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE circle_trust_scores
    ADD COLUMN IF NOT EXISTS network_coach_level INTEGER;

ALTER TABLE circle_trust_scores
    ADD CONSTRAINT ck_circle_trust_scores_network_signal
        CHECK (network_signal_score >= 0 AND network_signal_score <= 100);

ALTER TABLE circle_trust_scores
    ADD CONSTRAINT ck_circle_trust_scores_network_bonus
        CHECK (network_bonus_score >= 0 AND network_bonus_score <= 10);

ALTER TABLE circle_trust_scores
    ADD CONSTRAINT ck_circle_trust_scores_network_advisors
        CHECK (network_advisors_count >= 0);

ALTER TABLE circle_trust_scores
    ADD CONSTRAINT ck_circle_trust_scores_network_level
        CHECK (network_coach_level IS NULL OR network_coach_level >= 1);
