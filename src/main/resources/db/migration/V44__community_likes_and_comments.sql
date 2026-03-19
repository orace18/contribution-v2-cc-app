-- Post likes
CREATE TABLE post_likes (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id             UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    account_holder_id   UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_post_likes PRIMARY KEY (id),
    CONSTRAINT uq_post_likes_post_user UNIQUE (post_id, account_holder_id)
);

CREATE INDEX idx_post_likes_post_id ON post_likes(post_id);
CREATE INDEX idx_post_likes_account_holder_id ON post_likes(account_holder_id);

-- Post comments
CREATE TABLE post_comments (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id             UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    account_holder_id   UUID        NOT NULL,
    content             TEXT        NOT NULL,
    parent_comment_id   UUID        REFERENCES post_comments(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_post_comments PRIMARY KEY (id)
);

CREATE INDEX idx_post_comments_post_id ON post_comments(post_id);
CREATE INDEX idx_post_comments_parent_id ON post_comments(parent_comment_id);

