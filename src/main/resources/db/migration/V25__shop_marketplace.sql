CREATE TABLE IF NOT EXISTS shop_products (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    commission_percent NUMERIC(5,2) NOT NULL,
    cv_value NUMERIC(19,4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_shop_products_merchant FOREIGN KEY (merchant_id) REFERENCES account_holders(id),
    CONSTRAINT ck_shop_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_shop_products_commission_percent_range CHECK (commission_percent >= 0 AND commission_percent <= 100),
    CONSTRAINT ck_shop_products_cv_non_negative CHECK (cv_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shop_products_merchant_active
    ON shop_products (merchant_id, active);
CREATE INDEX IF NOT EXISTS idx_shop_products_active_name
    ON shop_products (active, name);

CREATE TABLE IF NOT EXISTS shop_orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    delivery_address_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_shop_orders_buyer FOREIGN KEY (buyer_id) REFERENCES account_holders(id),
    CONSTRAINT ck_shop_orders_status CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_shop_orders_total_non_negative CHECK (total_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shop_orders_buyer_created
    ON shop_orders (buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shop_orders_status_created
    ON shop_orders (status, created_at DESC);

CREATE TABLE IF NOT EXISTS shop_order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    commission_percent NUMERIC(5,2) NOT NULL,
    cv_value NUMERIC(19,4) NOT NULL,
    CONSTRAINT fk_shop_order_items_order FOREIGN KEY (order_id) REFERENCES shop_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_order_items_product FOREIGN KEY (product_id) REFERENCES shop_products(id),
    CONSTRAINT ck_shop_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_shop_order_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_shop_order_items_commission_percent_range CHECK (commission_percent >= 0 AND commission_percent <= 100),
    CONSTRAINT ck_shop_order_items_cv_non_negative CHECK (cv_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shop_order_items_order
    ON shop_order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_shop_order_items_product
    ON shop_order_items (product_id);

CREATE TABLE IF NOT EXISTS shop_commission_pool (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    merchant_amount NUMERIC(19,4) NOT NULL,
    platform_commission NUMERIC(19,4) NOT NULL,
    mlm_volume NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_shop_commission_pool_order FOREIGN KEY (order_id) REFERENCES shop_orders(id) ON DELETE CASCADE,
    CONSTRAINT uk_shop_commission_pool_order UNIQUE (order_id),
    CONSTRAINT ck_shop_commission_pool_merchant_non_negative CHECK (merchant_amount >= 0),
    CONSTRAINT ck_shop_commission_pool_platform_non_negative CHECK (platform_commission >= 0),
    CONSTRAINT ck_shop_commission_pool_mlm_non_negative CHECK (mlm_volume >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shop_commission_pool_order
    ON shop_commission_pool (order_id);
