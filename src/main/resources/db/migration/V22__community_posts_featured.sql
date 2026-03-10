CREATE TABLE IF NOT EXISTS community_posts (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    media_url VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE community_posts
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS featured_priority INTEGER,
    ADD COLUMN IF NOT EXISTS featured_at TIMESTAMPTZ;

ALTER TABLE community_posts
    DROP CONSTRAINT IF EXISTS ck_community_posts_featured_priority_positive;
ALTER TABLE community_posts
    ADD CONSTRAINT ck_community_posts_featured_priority_positive
        CHECK (featured_priority IS NULL OR featured_priority > 0);

CREATE INDEX IF NOT EXISTS idx_community_posts_created_at
    ON community_posts (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_community_posts_featured_priority
    ON community_posts (featured, featured_priority, featured_at DESC);
