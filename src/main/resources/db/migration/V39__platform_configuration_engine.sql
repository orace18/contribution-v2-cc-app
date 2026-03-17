CREATE TABLE IF NOT EXISTS platform_config_versions (
    id UUID PRIMARY KEY,
    config_type VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    created_by_admin_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_platform_config_versions_type_version
        UNIQUE (config_type, version)
);

CREATE INDEX IF NOT EXISTS idx_platform_config_versions_type_effective
    ON platform_config_versions (config_type, effective_date DESC, version DESC);
