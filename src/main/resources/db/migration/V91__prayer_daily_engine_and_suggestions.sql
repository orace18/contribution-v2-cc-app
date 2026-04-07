ALTER TABLE prayer_daily_completions
    DROP CONSTRAINT IF EXISTS ck_prayer_daily_completions_response_type;

ALTER TABLE prayer_daily_completions
    ADD CONSTRAINT ck_prayer_daily_completions_response_type
        CHECK (response_type IN ('PRAYED', 'AMEN'));

ALTER TABLE prayer_engagement_daily_checkins
    DROP CONSTRAINT IF EXISTS ck_prayer_checkins_response_type;

ALTER TABLE prayer_engagement_daily_checkins
    ADD CONSTRAINT ck_prayer_checkins_response_type
        CHECK (response_type IN ('PRAYED', 'AMEN'));

CREATE TABLE prayer_suggestion_templates (
    id           UUID PRIMARY KEY,
    template_key VARCHAR(64)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    content      TEXT         NOT NULL,
    category     VARCHAR(64)  NOT NULL,
    tags_csv     VARCHAR(255) NULL,
    audio_status VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    audio_url    VARCHAR(512) NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_prayer_suggestion_templates_key
        UNIQUE (template_key),
    CONSTRAINT ck_prayer_suggestion_templates_category
        CHECK (category IN ('BUSINESS_WISDOM', 'PROVISION', 'COURAGE', 'UNITY', 'DISCIPLINE', 'GRATITUDE')),
    CONSTRAINT ck_prayer_suggestion_templates_audio_status
        CHECK (audio_status IN ('NONE', 'PLACEHOLDER', 'CURATED_URL')),
    CONSTRAINT ck_prayer_suggestion_templates_audio_url
        CHECK (audio_status <> 'CURATED_URL' OR audio_url IS NOT NULL),
    CONSTRAINT ck_prayer_suggestion_templates_sort_order
        CHECK (sort_order >= 0)
);

CREATE INDEX idx_prayer_suggestion_templates_active_sort
    ON prayer_suggestion_templates (active, sort_order);

CREATE INDEX idx_prayer_suggestion_templates_category
    ON prayer_suggestion_templates (category);

INSERT INTO prayer_suggestion_templates (
    id,
    template_key,
    title,
    content,
    category,
    tags_csv,
    audio_status,
    audio_url,
    active,
    sort_order,
    created_at,
    updated_at
)
VALUES
    (
        gen_random_uuid(),
        'BUSINESS_WISDOM_PLANNING',
        'Sagesse pour tes decisions',
        'Seigneur, accorde-moi une sagesse claire pour planifier, discerner les bonnes opportunites et refuser les raccourcis qui detournent ma mission.',
        'BUSINESS_WISDOM',
        'business,wisdom,planning',
        'NONE',
        NULL,
        TRUE,
        10,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'PROVISION_CLIENTS',
        'Provision pour ton activite',
        'Pere, ouvre les bonnes portes, amene les bons clients et donne-moi la grace de gerer chaque ressource avec fidelite et paix.',
        'PROVISION',
        'provision,clients,finance',
        'PLACEHOLDER',
        NULL,
        TRUE,
        20,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'COURAGE_LEADERSHIP',
        'Courage pour conduire',
        'Jesus, fortifie mon coeur pour conduire avec courage, parler avec verite et rester stable meme quand les decisions sont exigeantes.',
        'COURAGE',
        'leadership,courage,decision',
        'NONE',
        NULL,
        TRUE,
        30,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'UNITY_RELATIONSHIPS',
        'Paix dans tes relations',
        'Seigneur, etablis la paix dans mes relations professionnelles et amene autour de moi des personnes qui construisent avec droiture.',
        'UNITY',
        'unity,relationships,team',
        'NONE',
        NULL,
        TRUE,
        40,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'DISCIPLINE_CONSISTENCY',
        'Discipline dans la duree',
        'Pere, donne-moi la discipline de rester constant, de finir ce que j ai commence et de te chercher avant chaque action importante.',
        'DISCIPLINE',
        'discipline,consistency,focus',
        'NONE',
        NULL,
        TRUE,
        50,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'GRATITUDE_GROWTH',
        'Reconnaissance et croissance',
        'Merci Seigneur pour ce que tu as deja accompli. Apprends-moi a grandir avec humilite, reconnaissance et confiance.',
        'GRATITUDE',
        'gratitude,growth,humility',
        'NONE',
        NULL,
        TRUE,
        60,
        now(),
        now()
    )
ON CONFLICT (template_key) DO UPDATE
SET
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    category = EXCLUDED.category,
    tags_csv = EXCLUDED.tags_csv,
    audio_status = EXCLUDED.audio_status,
    audio_url = EXCLUDED.audio_url,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
