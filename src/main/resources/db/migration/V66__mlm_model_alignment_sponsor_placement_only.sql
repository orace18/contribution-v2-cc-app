create table if not exists mlm_member_links (
    account_holder_id uuid primary key,
    sponsor_account_holder_id uuid null,
    placement_account_holder_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_mlm_member_links_account_holder
        foreign key (account_holder_id) references account_holders(id),
    constraint fk_mlm_member_links_sponsor
        foreign key (sponsor_account_holder_id) references account_holders(id),
    constraint fk_mlm_member_links_placement
        foreign key (placement_account_holder_id) references account_holders(id),
    constraint ck_mlm_member_links_no_self_sponsor
        check (sponsor_account_holder_id is null or sponsor_account_holder_id <> account_holder_id)
);

create index if not exists idx_mlm_member_links_sponsor
    on mlm_member_links (sponsor_account_holder_id);

create index if not exists idx_mlm_member_links_placement
    on mlm_member_links (placement_account_holder_id);

insert into mlm_member_links (
    account_holder_id,
    sponsor_account_holder_id,
    placement_account_holder_id,
    created_at,
    updated_at
)
select latest.account_holder_id,
       latest.sponsor_account_holder_id,
       latest.placement_parent_id,
       latest.created_at,
       now()
from (
         select distinct on (account_holder_id)
                account_holder_id,
                sponsor_account_holder_id,
                placement_parent_id,
                created_at
         from mlm_placement_audit_logs
         order by account_holder_id, created_at desc
     ) latest
on conflict (account_holder_id) do update
    set sponsor_account_holder_id = excluded.sponsor_account_holder_id,
        placement_account_holder_id = excluded.placement_account_holder_id,
        updated_at = now();

insert into mlm_member_links (
    account_holder_id,
    sponsor_account_holder_id,
    placement_account_holder_id,
    created_at,
    updated_at
)
select r.descendant_id,
       null,
       r.descendant_id,
       now(),
       now()
from mlm_relationships r
where r.level = 0
  and not exists (
    select 1
    from mlm_member_links links
    where links.account_holder_id = r.descendant_id
)
on conflict (account_holder_id) do nothing;

alter table mlm_volume_ledger
    drop constraint if exists ck_mlm_volume_ledger_leg_position_positive;

drop index if exists idx_mlm_volume_ledger_leg_position;

alter table mlm_volume_ledger
    drop column if exists leg_position;

delete from mlm_leg_carryover;

alter table mlm_leg_carryover
    drop constraint if exists pk_mlm_leg_carryover;

alter table mlm_leg_carryover
    drop constraint if exists ck_mlm_leg_carryover_leg_position_positive;

drop index if exists idx_mlm_leg_carryover_leg_position;

alter table mlm_leg_carryover
    drop column if exists leg_position;

alter table mlm_leg_carryover
    add column if not exists placement_child_id uuid;

update mlm_leg_carryover
set placement_child_id = member_id
where placement_child_id is null;

alter table mlm_leg_carryover
    alter column placement_child_id set not null;

alter table mlm_leg_carryover
    add constraint fk_mlm_leg_carryover_placement_child
        foreign key (placement_child_id) references account_holders(id);

alter table mlm_leg_carryover
    add constraint pk_mlm_leg_carryover
        primary key (member_id, placement_child_id, week_id);

create index if not exists idx_mlm_leg_carryover_placement_child
    on mlm_leg_carryover (placement_child_id);
