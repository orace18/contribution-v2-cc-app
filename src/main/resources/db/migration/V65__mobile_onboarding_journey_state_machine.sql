create table if not exists mobile_onboarding_journeys (
    id uuid primary key,
    account_holder_id uuid not null,
    status varchar(32) not null,
    country_code varchar(3),
    sponsor_set boolean not null default false,
    sponsor_account_holder_id uuid,
    plan_id uuid,
    plan_code varchar(64),
    payment_operation_id uuid,
    payment_reference varchar(128),
    network_linked boolean not null default false,
    onboarding_completed boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_mobile_onboarding_journeys_account_holder unique (account_holder_id),
    constraint fk_mobile_onboarding_journeys_account_holder
        foreign key (account_holder_id) references account_holders(id),
    constraint fk_mobile_onboarding_journeys_sponsor
        foreign key (sponsor_account_holder_id) references account_holders(id),
    constraint fk_mobile_onboarding_journeys_plan
        foreign key (plan_id) references subscription_plans(id),
    constraint fk_mobile_onboarding_journeys_payment_operation
        foreign key (payment_operation_id) references operations(id),
    constraint ck_mobile_onboarding_journeys_status
        check (status in (
            'REGISTERED',
            'AUTHENTICATED',
            'COUNTRY_SELECTED',
            'SPONSOR_SET',
            'PLAN_SELECTED',
            'PAYMENT_PENDING',
            'PAYMENT_CONFIRMED',
            'ACTIVE_MEMBER',
            'NETWORK_LINKED',
            'ONBOARDING_COMPLETED'
        ))
);

create index if not exists idx_mobile_onboarding_journeys_status
    on mobile_onboarding_journeys(status);

create index if not exists idx_mobile_onboarding_journeys_plan_id
    on mobile_onboarding_journeys(plan_id);

create index if not exists idx_mobile_onboarding_journeys_payment_operation_id
    on mobile_onboarding_journeys(payment_operation_id);
