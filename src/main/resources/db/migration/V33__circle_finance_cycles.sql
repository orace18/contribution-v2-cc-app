CREATE TABLE circle_cycles (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    cycle_number INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_cycles_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT uk_circle_cycles_circle_cycle_number UNIQUE (circle_id, cycle_number),
    CONSTRAINT ck_circle_cycles_cycle_number CHECK (cycle_number > 0),
    CONSTRAINT ck_circle_cycles_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_circle_cycles_status CHECK (status IN ('FORMING', 'ACTIVE', 'COMPLETED', 'DEFAULTED'))
);

CREATE INDEX idx_circle_cycles_circle_status ON circle_cycles (circle_id, status);
CREATE UNIQUE INDEX uk_circle_cycles_one_active
    ON circle_cycles (circle_id)
    WHERE status = 'ACTIVE';

CREATE TABLE circle_contributions (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL,
    member_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ,
    CONSTRAINT fk_circle_contributions_cycle FOREIGN KEY (cycle_id) REFERENCES circle_cycles(id),
    CONSTRAINT fk_circle_contributions_member FOREIGN KEY (member_id) REFERENCES circle_members(id),
    CONSTRAINT ck_circle_contributions_amount CHECK (amount > 0),
    CONSTRAINT ck_circle_contributions_status CHECK (status IN ('PENDING', 'PAID', 'LATE'))
);

CREATE INDEX idx_circle_contributions_cycle_status ON circle_contributions (cycle_id, status);
CREATE INDEX idx_circle_contributions_member_status ON circle_contributions (member_id, status);

CREATE TABLE circle_loans (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    interest NUMERIC(5,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    approved_at TIMESTAMPTZ,
    disbursed_at TIMESTAMPTZ,
    CONSTRAINT fk_circle_loans_cycle FOREIGN KEY (cycle_id) REFERENCES circle_cycles(id),
    CONSTRAINT fk_circle_loans_borrower FOREIGN KEY (borrower_id) REFERENCES circle_members(id),
    CONSTRAINT ck_circle_loans_amount CHECK (amount > 0),
    CONSTRAINT ck_circle_loans_interest CHECK (interest >= 0),
    CONSTRAINT ck_circle_loans_status CHECK (status IN ('REQUESTED', 'APPROVED', 'DISBURSED', 'REPAID', 'DEFAULTED'))
);

CREATE INDEX idx_circle_loans_cycle_status ON circle_loans (cycle_id, status);
CREATE INDEX idx_circle_loans_borrower_status ON circle_loans (borrower_id, status);
CREATE UNIQUE INDEX uk_circle_loans_borrower_one_active
    ON circle_loans (borrower_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'DISBURSED');

CREATE TABLE circle_repayments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT fk_circle_repayments_loan FOREIGN KEY (loan_id) REFERENCES circle_loans(id),
    CONSTRAINT ck_circle_repayments_amount CHECK (amount > 0),
    CONSTRAINT ck_circle_repayments_status CHECK (status IN ('PENDING', 'PAID', 'LATE'))
);

CREATE INDEX idx_circle_repayments_loan_status ON circle_repayments (loan_id, status);
