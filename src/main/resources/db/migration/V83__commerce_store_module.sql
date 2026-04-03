CREATE TABLE IF NOT EXISTS commerce_merchants (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    country_code VARCHAR(3),
    status VARCHAR(16) NOT NULL,
    logo_url VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_commerce_merchants_account_holder UNIQUE (account_holder_id),
    CONSTRAINT fk_commerce_merchants_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_commerce_merchants_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_commerce_merchants_account_holder
    ON commerce_merchants (account_holder_id);

CREATE TABLE IF NOT EXISTS commerce_products (
    product_id UUID PRIMARY KEY,
    dsb_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_commerce_products_product FOREIGN KEY (product_id) REFERENCES shop_products(id) ON DELETE CASCADE,
    CONSTRAINT ck_commerce_products_dsb_non_negative CHECK (dsb_amount >= 0)
);

CREATE TABLE IF NOT EXISTS commerce_promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    owner_account_holder_id UUID NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    country VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expiry_date TIMESTAMPTZ NOT NULL,
    usage_limit INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_commerce_promo_codes_code UNIQUE (code),
    CONSTRAINT fk_commerce_promo_codes_owner FOREIGN KEY (owner_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_commerce_promo_codes_discount_non_negative CHECK (discount_amount >= 0),
    CONSTRAINT ck_commerce_promo_codes_usage_limit_positive CHECK (usage_limit > 0),
    CONSTRAINT ck_commerce_promo_codes_used_count_non_negative CHECK (used_count >= 0),
    CONSTRAINT ck_commerce_promo_codes_status CHECK (status IN ('ACTIVE', 'DISABLED', 'EXPIRED', 'USAGE_LIMIT_REACHED'))
);

CREATE INDEX IF NOT EXISTS idx_commerce_promo_codes_owner_created
    ON commerce_promo_codes (owner_account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_commerce_promo_codes_country_status
    ON commerce_promo_codes (country, status);

CREATE TABLE IF NOT EXISTS commerce_orders (
    order_id UUID PRIMARY KEY,
    buyer_account_holder_id UUID NOT NULL,
    merchant_account_holder_id UUID NOT NULL,
    country_code VARCHAR(3),
    currency VARCHAR(3) NOT NULL,
    promo_code_id UUID,
    gross_amount NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    net_paid_amount NUMERIC(19,4) NOT NULL,
    total_dsb_amount NUMERIC(19,4) NOT NULL,
    platform_commission NUMERIC(19,4) NOT NULL,
    merchant_net_amount NUMERIC(19,4) NOT NULL,
    volume_cv NUMERIC(19,4) NOT NULL,
    distribution_mode VARCHAR(32) NOT NULL,
    settlement_status VARCHAR(32) NOT NULL,
    delivery_confirmed_at TIMESTAMPTZ,
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_commerce_orders_order FOREIGN KEY (order_id) REFERENCES shop_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_orders_buyer FOREIGN KEY (buyer_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_commerce_orders_merchant FOREIGN KEY (merchant_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_commerce_orders_promo_code FOREIGN KEY (promo_code_id) REFERENCES commerce_promo_codes(id),
    CONSTRAINT ck_commerce_orders_gross_non_negative CHECK (gross_amount >= 0),
    CONSTRAINT ck_commerce_orders_discount_non_negative CHECK (discount_amount >= 0),
    CONSTRAINT ck_commerce_orders_net_non_negative CHECK (net_paid_amount >= 0),
    CONSTRAINT ck_commerce_orders_dsb_non_negative CHECK (total_dsb_amount >= 0),
    CONSTRAINT ck_commerce_orders_platform_non_negative CHECK (platform_commission >= 0),
    CONSTRAINT ck_commerce_orders_merchant_non_negative CHECK (merchant_net_amount >= 0),
    CONSTRAINT ck_commerce_orders_volume_non_negative CHECK (volume_cv >= 0),
    CONSTRAINT ck_commerce_orders_distribution_mode CHECK (distribution_mode IN ('NONE', 'PROMO_CODE', 'SPONSOR_CHAIN')),
    CONSTRAINT ck_commerce_orders_settlement_status CHECK (settlement_status IN ('PENDING_DELIVERY', 'SETTLED', 'FAILED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_commerce_orders_buyer_created
    ON commerce_orders (buyer_account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_commerce_orders_merchant_created
    ON commerce_orders (merchant_account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_commerce_orders_status_created
    ON commerce_orders (settlement_status, created_at DESC);

CREATE TABLE IF NOT EXISTS commerce_dsb_payouts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    beneficiary_account_holder_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    operation_id UUID,
    reversal_operation_id UUID,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_commerce_dsb_payouts_order_role UNIQUE (order_id, role),
    CONSTRAINT fk_commerce_dsb_payouts_order FOREIGN KEY (order_id) REFERENCES commerce_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_dsb_payouts_beneficiary FOREIGN KEY (beneficiary_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_commerce_dsb_payouts_operation FOREIGN KEY (operation_id) REFERENCES operations(id),
    CONSTRAINT fk_commerce_dsb_payouts_reversal_operation FOREIGN KEY (reversal_operation_id) REFERENCES operations(id),
    CONSTRAINT ck_commerce_dsb_payouts_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_commerce_dsb_payouts_role CHECK (role IN ('PROMO_OWNER', 'SPONSOR_G1', 'SPONSOR_G2', 'SPONSOR_G3')),
    CONSTRAINT ck_commerce_dsb_payouts_status CHECK (status IN ('PAID', 'REVERSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_commerce_dsb_payouts_order_created
    ON commerce_dsb_payouts (order_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_commerce_dsb_payouts_beneficiary_created
    ON commerce_dsb_payouts (beneficiary_account_holder_id, created_at DESC);
