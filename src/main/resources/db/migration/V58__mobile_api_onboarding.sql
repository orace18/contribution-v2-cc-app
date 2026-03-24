create table if not exists mobile_plan_selections (
    id uuid primary key,
    account_holder_id uuid not null,
    plan_id uuid not null,
    plan_code varchar(64) not null,
    plan_name varchar(128) not null,
    country_code varchar(3) not null,
    currency varchar(3) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_mobile_plan_selections_account_holder unique (account_holder_id),
    constraint fk_mobile_plan_selections_account_holder foreign key (account_holder_id) references account_holders(id),
    constraint fk_mobile_plan_selections_plan foreign key (plan_id) references subscription_plans(id)
);

create index if not exists idx_mobile_plan_selections_plan_id
    on mobile_plan_selections(plan_id);
