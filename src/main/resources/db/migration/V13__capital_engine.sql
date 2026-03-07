CREATE TABLE funds (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    total_capital NUMERIC(19,4) NOT NULL,
    available_capital NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_funds_provider CHECK (provider IN ('MICROFINANCE_INSTITUTION', 'INVESTOR')),
    CONSTRAINT ck_funds_total_capital CHECK (total_capital > 0),
    CONSTRAINT ck_funds_available_capital CHECK (available_capital >= 0 AND available_capital <= total_capital)
);

CREATE INDEX idx_funds_provider ON funds(provider);

CREATE TABLE capital_pools (
    id UUID PRIMARY KEY,
    fund_id UUID NOT NULL,
    circle_id UUID NOT NULL,
    allocated_amount NUMERIC(19,4) NOT NULL,
    used_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT fk_capital_pools_fund FOREIGN KEY (fund_id) REFERENCES funds(id),
    CONSTRAINT fk_capital_pools_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT uk_capital_pools_fund_circle UNIQUE (fund_id, circle_id),
    CONSTRAINT ck_capital_pools_allocated_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_capital_pools_used_amount CHECK (used_amount >= 0 AND used_amount <= allocated_amount)
);

CREATE INDEX idx_capital_pools_fund_id ON capital_pools(fund_id);
CREATE INDEX idx_capital_pools_circle_id ON capital_pools(circle_id);

CREATE TABLE loan_funding (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    fund_id UUID NOT NULL,
    funded_amount NUMERIC(19,4) NOT NULL,
    funded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_funding_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
    CONSTRAINT fk_loan_funding_fund FOREIGN KEY (fund_id) REFERENCES funds(id),
    CONSTRAINT uk_loan_funding_loan_id UNIQUE (loan_id),
    CONSTRAINT ck_loan_funding_amount CHECK (funded_amount > 0)
);

CREATE INDEX idx_loan_funding_fund_id ON loan_funding(fund_id);
