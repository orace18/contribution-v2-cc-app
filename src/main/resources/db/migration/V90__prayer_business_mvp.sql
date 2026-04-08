CREATE TABLE prayer_posts (
    id                       UUID PRIMARY KEY,
    post_type                VARCHAR(16)  NOT NULL,
    lifecycle_status         VARCHAR(16)  NOT NULL,
    author_account_holder_id UUID         NULL,
    actor_user_id            UUID         NULL,
    created_by_admin_user_id UUID         NULL,
    title                    VARCHAR(160) NULL,
    content                  TEXT         NOT NULL,
    visibility               VARCHAR(16)  NOT NULL,
    audience_circle_id       UUID         NULL,
    audience_church_ref      VARCHAR(128) NULL,
    audience_country_code    VARCHAR(3)   NULL,
    deep_link_route_key      VARCHAR(64)  NULL,
    deep_link_label          VARCHAR(160) NULL,
    deep_link_params_json    TEXT         NOT NULL DEFAULT '{}',
    client_request_id        UUID         NULL,
    amen_count               INTEGER      NOT NULL DEFAULT 0,
    support_count            INTEGER      NOT NULL DEFAULT 0,
    comment_count            INTEGER      NOT NULL DEFAULT 0,
    published_at             TIMESTAMPTZ  NULL,
    archived_at              TIMESTAMPTZ  NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_posts_author_account_holder
        FOREIGN KEY (author_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_posts_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT fk_prayer_posts_created_by_admin_user
        FOREIGN KEY (created_by_admin_user_id) REFERENCES admin_users(id),
    CONSTRAINT ck_prayer_posts_post_type
        CHECK (post_type IN ('REQUEST', 'OFFICIAL')),
    CONSTRAINT ck_prayer_posts_lifecycle_status
        CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'MODERATED', 'DELETED')),
    CONSTRAINT ck_prayer_posts_visibility
        CHECK (visibility IN ('PRIVATE', 'CIRCLE', 'CHURCH', 'COMMUNITY', 'GLOBAL')),
    CONSTRAINT ck_prayer_posts_official_visibility
        CHECK (
            post_type <> 'OFFICIAL'
            OR visibility IN ('CHURCH', 'COMMUNITY', 'GLOBAL')
        ),
    CONSTRAINT ck_prayer_posts_deep_link_route_key
        CHECK (
            deep_link_route_key IS NULL
            OR deep_link_route_key IN (
                'PRAYER_DAY',
                'PRAYER_FEED',
                'WALLET_HOME',
                'WALLET_FUND',
                'STORE_HOME',
                'MEMBERSHIP_PLANS',
                'FINANCING_HOME',
                'NOTIFICATION_CENTER'
            )
        ),
    CONSTRAINT ck_prayer_posts_counts_non_negative
        CHECK (amen_count >= 0 AND support_count >= 0 AND comment_count >= 0)
);

CREATE INDEX idx_prayer_posts_status_published
    ON prayer_posts (lifecycle_status, published_at);

CREATE INDEX idx_prayer_posts_visibility_created
    ON prayer_posts (visibility, created_at);

CREATE INDEX idx_prayer_posts_author_created
    ON prayer_posts (author_account_holder_id, created_at);

CREATE UNIQUE INDEX uk_prayer_posts_author_client_request
    ON prayer_posts (author_account_holder_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE TABLE prayer_post_media_refs (
    id            UUID PRIMARY KEY,
    post_id       UUID        NOT NULL,
    media_file_id UUID        NOT NULL,
    usage_type    VARCHAR(16) NOT NULL,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT fk_prayer_post_media_refs_post
        FOREIGN KEY (post_id) REFERENCES prayer_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_post_media_refs_media
        FOREIGN KEY (media_file_id) REFERENCES media_files(id),
    CONSTRAINT ck_prayer_post_media_refs_usage_type
        CHECK (usage_type IN ('COVER', 'INLINE', 'AUDIO', 'ATTACHMENT')),
    CONSTRAINT ck_prayer_post_media_refs_sort_order
        CHECK (sort_order >= 0)
);

CREATE INDEX idx_prayer_post_media_refs_post
    ON prayer_post_media_refs (post_id);

CREATE TABLE prayer_amens (
    id                UUID PRIMARY KEY,
    post_id           UUID        NOT NULL,
    account_holder_id UUID        NOT NULL,
    actor_user_id     UUID        NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_prayer_amens_post
        FOREIGN KEY (post_id) REFERENCES prayer_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_amens_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_amens_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_prayer_amens_post_account
    ON prayer_amens (post_id, account_holder_id);

CREATE INDEX idx_prayer_amens_account_created
    ON prayer_amens (account_holder_id, created_at);

CREATE UNIQUE INDEX uk_prayer_amens_post_account
    ON prayer_amens (post_id, account_holder_id);

CREATE TABLE prayer_supports (
    id                UUID PRIMARY KEY,
    post_id           UUID        NOT NULL,
    account_holder_id UUID        NOT NULL,
    actor_user_id     UUID        NULL,
    support_type      VARCHAR(32) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_prayer_supports_post
        FOREIGN KEY (post_id) REFERENCES prayer_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_supports_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_supports_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_prayer_supports_support_type
        CHECK (support_type IN ('PRAYED'))
);

CREATE INDEX idx_prayer_supports_post_account
    ON prayer_supports (post_id, account_holder_id);

CREATE INDEX idx_prayer_supports_account_created
    ON prayer_supports (account_holder_id, created_at);

CREATE UNIQUE INDEX uk_prayer_supports_post_account
    ON prayer_supports (post_id, account_holder_id);

CREATE TABLE prayer_comments (
    id                UUID PRIMARY KEY,
    post_id           UUID         NOT NULL,
    account_holder_id UUID         NOT NULL,
    actor_user_id     UUID         NULL,
    client_request_id UUID         NULL,
    content           VARCHAR(2000) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_comments_post
        FOREIGN KEY (post_id) REFERENCES prayer_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_comments_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_comments_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_prayer_comments_post_created
    ON prayer_comments (post_id, created_at);

CREATE INDEX idx_prayer_comments_account_created
    ON prayer_comments (account_holder_id, created_at);

CREATE UNIQUE INDEX uk_prayer_comments_account_client_request
    ON prayer_comments (account_holder_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE TABLE prayer_daily_contents (
    id                       UUID PRIMARY KEY,
    prayer_date              DATE         NOT NULL,
    title                    VARCHAR(160) NULL,
    scripture_reference      VARCHAR(160) NULL,
    content                  TEXT         NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    created_by_admin_user_id UUID         NOT NULL,
    published_at             TIMESTAMPTZ  NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_daily_contents_created_by_admin_user
        FOREIGN KEY (created_by_admin_user_id) REFERENCES admin_users(id),
    CONSTRAINT ck_prayer_daily_contents_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_prayer_daily_contents_prayer_date
    ON prayer_daily_contents (prayer_date);

CREATE INDEX idx_prayer_daily_contents_date_status
    ON prayer_daily_contents (prayer_date, status);

CREATE TABLE prayer_daily_media_refs (
    id              UUID PRIMARY KEY,
    daily_prayer_id UUID        NOT NULL,
    media_file_id   UUID        NOT NULL,
    usage_type      VARCHAR(16) NOT NULL,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT fk_prayer_daily_media_refs_daily
        FOREIGN KEY (daily_prayer_id) REFERENCES prayer_daily_contents(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_daily_media_refs_media
        FOREIGN KEY (media_file_id) REFERENCES media_files(id),
    CONSTRAINT ck_prayer_daily_media_refs_usage_type
        CHECK (usage_type IN ('COVER', 'INLINE', 'AUDIO', 'ATTACHMENT')),
    CONSTRAINT ck_prayer_daily_media_refs_sort_order
        CHECK (sort_order >= 0)
);

CREATE INDEX idx_prayer_daily_media_refs_daily
    ON prayer_daily_media_refs (daily_prayer_id);

CREATE TABLE prayer_daily_completions (
    id                UUID PRIMARY KEY,
    daily_prayer_id   UUID         NOT NULL,
    account_holder_id UUID         NOT NULL,
    actor_user_id     UUID         NULL,
    prayer_date       DATE         NOT NULL,
    timezone          VARCHAR(64)  NOT NULL,
    response_type     VARCHAR(32)  NOT NULL,
    completed_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_daily_completions_daily
        FOREIGN KEY (daily_prayer_id) REFERENCES prayer_daily_contents(id) ON DELETE CASCADE,
    CONSTRAINT fk_prayer_daily_completions_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_daily_completions_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_prayer_daily_completions_response_type
        CHECK (response_type IN ('COMPLETED'))
);

CREATE INDEX idx_prayer_daily_completions_account_date
    ON prayer_daily_completions (account_holder_id, prayer_date);

CREATE INDEX idx_prayer_daily_completions_daily
    ON prayer_daily_completions (daily_prayer_id);

CREATE UNIQUE INDEX uk_prayer_daily_completions_account_date
    ON prayer_daily_completions (account_holder_id, prayer_date);

CREATE TABLE prayer_engagement_daily_checkins (
    id                  UUID PRIMARY KEY,
    account_holder_id   UUID         NOT NULL,
    actor_user_id       UUID         NULL,
    local_date          DATE         NOT NULL,
    timezone            VARCHAR(64)  NOT NULL,
    response_type       VARCHAR(32)  NOT NULL,
    source_reference_id UUID         NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_checkins_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_prayer_checkins_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_prayer_checkins_response_type
        CHECK (response_type IN ('COMPLETED'))
);

CREATE INDEX idx_prayer_checkins_account_date
    ON prayer_engagement_daily_checkins (account_holder_id, local_date);

CREATE UNIQUE INDEX uk_prayer_checkins_account_date
    ON prayer_engagement_daily_checkins (account_holder_id, local_date);

CREATE TABLE prayer_daily_mission_definitions (
    id           UUID PRIMARY KEY,
    mission_code VARCHAR(64)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    description  VARCHAR(255) NOT NULL,
    target_value INTEGER      NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_prayer_daily_mission_definitions_mission_code
        CHECK (mission_code IN ('READ_DAILY_PRAYER', 'SUPPORT_ONE_REQUEST', 'COMMENT_ONE_POST')),
    CONSTRAINT ck_prayer_daily_mission_definitions_target_value
        CHECK (target_value > 0),
    CONSTRAINT ck_prayer_daily_mission_definitions_sort_order
        CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX uk_prayer_daily_mission_definitions_code
    ON prayer_daily_mission_definitions (mission_code);

CREATE INDEX idx_prayer_daily_mission_definitions_active_sort
    ON prayer_daily_mission_definitions (active, sort_order);

CREATE TABLE prayer_daily_mission_progress (
    id                UUID PRIMARY KEY,
    account_holder_id UUID         NOT NULL,
    mission_date      DATE         NOT NULL,
    mission_code      VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    progress_value    INTEGER      NOT NULL DEFAULT 0,
    target_value      INTEGER      NOT NULL,
    completed_at      TIMESTAMPTZ  NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_prayer_daily_mission_progress_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_prayer_daily_mission_progress_mission_code
        CHECK (mission_code IN ('READ_DAILY_PRAYER', 'SUPPORT_ONE_REQUEST', 'COMMENT_ONE_POST')),
    CONSTRAINT ck_prayer_daily_mission_progress_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT ck_prayer_daily_mission_progress_progress_value
        CHECK (progress_value >= 0),
    CONSTRAINT ck_prayer_daily_mission_progress_target_value
        CHECK (target_value > 0)
);

CREATE INDEX idx_prayer_daily_mission_progress_account_date
    ON prayer_daily_mission_progress (account_holder_id, mission_date);

CREATE UNIQUE INDEX uk_prayer_daily_mission_progress_account_date_code
    ON prayer_daily_mission_progress (account_holder_id, mission_date, mission_code);

INSERT INTO prayer_daily_mission_definitions (
    id,
    mission_code,
    title,
    description,
    target_value,
    active,
    sort_order,
    created_at,
    updated_at
)
VALUES
    (
        gen_random_uuid(),
        'READ_DAILY_PRAYER',
        'Lire la priere du jour',
        'Ouvre et termine la priere quotidienne du module Business Prayer.',
        1,
        TRUE,
        10,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'SUPPORT_ONE_REQUEST',
        'Soutenir une demande',
        'Marque au moins une demande avec la reaction Je prie pour cette personne.',
        1,
        TRUE,
        20,
        now(),
        now()
    ),
    (
        gen_random_uuid(),
        'COMMENT_ONE_POST',
        'Encourager par un commentaire',
        'Publie un commentaire d encouragement sur une publication visible.',
        1,
        TRUE,
        30,
        now(),
        now()
    )
ON CONFLICT (mission_code) DO NOTHING;
