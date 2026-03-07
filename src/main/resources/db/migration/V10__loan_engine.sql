CREATE TABLE loans (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    circle_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    interest_rate NUMERIC(5,2) NOT NULL,
    duration_months INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    approved_at TIMESTAMPTZ,
    disbursed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loans_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_loans_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_loans_amount CHECK (amount > 0),
    CONSTRAINT ck_loans_interest_rate CHECK (interest_rate >= 0),
    CONSTRAINT ck_loans_duration_months CHECK (duration_months > 0),
    CONSTRAINT ck_loans_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DISBURSED', 'ACTIVE', 'REPAID', 'DEFAULTED'))
);

CREATE INDEX idx_loans_account_holder_status ON loans (account_holder_id, status);
CREATE INDEX idx_loans_circle_id ON loans (circle_id);

CREATE TABLE loan_repayments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(128),
    paid_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_repayments_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
    CONSTRAINT uk_loan_repayments_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_loan_repayments_amount CHECK (amount > 0)
);

CREATE INDEX idx_loan_repayments_loan_id ON loan_repayments (loan_id);
CREATE INDEX idx_loan_repayments_paid_at ON loan_repayments (paid_at);

CREATE TABLE repayment_schedules (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    amount_due NUMERIC(19,4) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT fk_repayment_schedules_loan FOREIGN KEY (loan_id) REFERENCES loans(id),
    CONSTRAINT uk_repayment_schedules_loan_installment UNIQUE (loan_id, installment_number),
    CONSTRAINT ck_repayment_schedules_installment CHECK (installment_number > 0),
    CONSTRAINT ck_repayment_schedules_amount_due CHECK (amount_due > 0),
    CONSTRAINT ck_repayment_schedules_status CHECK (status IN ('PENDING', 'PAID', 'LATE'))
);

CREATE INDEX idx_repayment_schedules_loan_id ON repayment_schedules (loan_id);
CREATE INDEX idx_repayment_schedules_due_date ON repayment_schedules (due_date);

CREATE TABLE credit_eligibility (
    account_holder_id UUID PRIMARY KEY,
    circle_id UUID,
    rms_score NUMERIC(5,2) NOT NULL,
    circle_score NUMERIC(5,2) NOT NULL,
    eligibility_score NUMERIC(5,2) NOT NULL,
    max_eligible_amount NUMERIC(19,4) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_credit_eligibility_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_credit_eligibility_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_credit_eligibility_rms_score CHECK (rms_score >= 0 AND rms_score <= 100),
    CONSTRAINT ck_credit_eligibility_circle_score CHECK (circle_score >= 0 AND circle_score <= 100),
    CONSTRAINT ck_credit_eligibility_eligibility_score CHECK (eligibility_score >= 0 AND eligibility_score <= 100),
    CONSTRAINT ck_credit_eligibility_max_amount CHECK (max_eligible_amount >= 0)
);

CREATE INDEX idx_credit_eligibility_circle_id ON credit_eligibility (circle_id);
