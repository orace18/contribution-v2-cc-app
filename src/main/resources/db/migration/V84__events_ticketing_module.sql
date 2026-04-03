CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    country VARCHAR(3) NOT NULL,
    start_date_time TIMESTAMPTZ NOT NULL,
    end_date_time TIMESTAMPTZ NOT NULL,
    location_type VARCHAR(16) NOT NULL,
    location_address VARCHAR(512),
    video_link VARCHAR(512),
    organizer_account_holder_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_events_location_type CHECK (location_type IN ('ONLINE', 'PHYSICAL', 'HYBRID')),
    CONSTRAINT ck_events_time_range CHECK (end_date_time > start_date_time)
);

CREATE INDEX IF NOT EXISTS idx_events_status_start
    ON events (status, start_date_time);

CREATE INDEX IF NOT EXISTS idx_events_organizer_created
    ON events (organizer_account_holder_id, created_at DESC);

CREATE TABLE IF NOT EXISTS event_speakers (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    photo VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_event_speakers_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_event_speakers_event
    ON event_speakers (event_id, created_at ASC);

CREATE TABLE IF NOT EXISTS event_ticket_types (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    stock INTEGER NOT NULL,
    cv NUMERIC(19,4) NOT NULL,
    platform_commission_rate NUMERIC(5,2) NOT NULL,
    dsb_amount NUMERIC(19,4) NOT NULL,
    shop_product_id UUID,
    product_sync_status VARCHAR(16) NOT NULL,
    product_sync_error VARCHAR(255),
    product_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_ticket_types_event_name UNIQUE (event_id, name),
    CONSTRAINT uk_event_ticket_types_shop_product UNIQUE (shop_product_id),
    CONSTRAINT fk_event_ticket_types_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_ticket_types_shop_product FOREIGN KEY (shop_product_id) REFERENCES shop_products(id),
    CONSTRAINT ck_event_ticket_types_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT ck_event_ticket_types_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_event_ticket_types_cv_non_negative CHECK (cv >= 0),
    CONSTRAINT ck_event_ticket_types_dsb_non_negative CHECK (dsb_amount >= 0),
    CONSTRAINT ck_event_ticket_types_platform_rate_non_negative CHECK (platform_commission_rate >= 0 AND platform_commission_rate <= 100),
    CONSTRAINT ck_event_ticket_types_sync_status CHECK (product_sync_status IN ('PENDING', 'SYNCED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_event_ticket_types_event_created
    ON event_ticket_types (event_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_event_ticket_types_stock
    ON event_ticket_types (stock);

CREATE TABLE IF NOT EXISTS event_assignments (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_assignments_event_account_role UNIQUE (event_id, account_holder_id, role),
    CONSTRAINT fk_event_assignments_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_assignments_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_event_assignments_role CHECK (role IN ('ORGANIZER', 'CONTROL_AGENT'))
);

CREATE INDEX IF NOT EXISTS idx_event_assignments_account_role
    ON event_assignments (account_holder_id, role);

CREATE TABLE IF NOT EXISTS event_tickets (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    order_id UUID NOT NULL,
    buyer_account_holder_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    qr_code TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    checkin_timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_tickets_qr_code UNIQUE (qr_code),
    CONSTRAINT fk_event_tickets_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_tickets_order FOREIGN KEY (order_id) REFERENCES shop_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_tickets_buyer FOREIGN KEY (buyer_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_event_tickets_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES event_ticket_types(id),
    CONSTRAINT ck_event_tickets_status CHECK (status IN ('VALID', 'USED', 'INVALID', 'REFUNDED'))
);

CREATE INDEX IF NOT EXISTS idx_event_tickets_buyer_created
    ON event_tickets (buyer_account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_event_tickets_event_status
    ON event_tickets (event_id, status);

CREATE INDEX IF NOT EXISTS idx_event_tickets_order
    ON event_tickets (order_id);

CREATE TABLE IF NOT EXISTS event_store_sync_jobs (
    id UUID PRIMARY KEY,
    ticket_type_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_event_store_sync_jobs_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES event_ticket_types(id) ON DELETE CASCADE,
    CONSTRAINT ck_event_store_sync_jobs_type CHECK (type IN ('UPSERT_PRODUCT', 'REFRESH_PRODUCT_STATE')),
    CONSTRAINT ck_event_store_sync_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_event_store_sync_jobs_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_event_store_sync_jobs_status_created
    ON event_store_sync_jobs (status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_event_store_sync_jobs_ticket_type
    ON event_store_sync_jobs (ticket_type_id, created_at DESC);

CREATE TABLE IF NOT EXISTS event_checkin_attempts (
    id UUID PRIMARY KEY,
    client_scan_id VARCHAR(64),
    source_device_id VARCHAR(128),
    event_id UUID,
    ticket_id UUID,
    qr_hash VARCHAR(128) NOT NULL,
    client_scanned_at TIMESTAMPTZ,
    result VARCHAR(16) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_checkin_attempts_client_scan UNIQUE (client_scan_id),
    CONSTRAINT fk_event_checkin_attempts_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT fk_event_checkin_attempts_ticket FOREIGN KEY (ticket_id) REFERENCES event_tickets(id),
    CONSTRAINT ck_event_checkin_attempts_result CHECK (result IN ('VALID', 'ALREADY_USED', 'INVALID'))
);

CREATE INDEX IF NOT EXISTS idx_event_checkin_attempts_ticket_processed
    ON event_checkin_attempts (ticket_id, processed_at DESC);

CREATE INDEX IF NOT EXISTS idx_event_checkin_attempts_qr_processed
    ON event_checkin_attempts (qr_hash, processed_at DESC);
