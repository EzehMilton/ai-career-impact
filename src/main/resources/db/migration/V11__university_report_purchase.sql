create table university_report_entitlement (
    entitlement_id uuid primary key,
    assessment_id uuid not null unique references university_assessment(id) on delete cascade,
    report_tier varchar(16) not null,
    status varchar(24) not null,
    payment_provider varchar(32) not null,
    provider_checkout_session_id text not null unique,
    checkout_url text not null,
    price_configuration_key text not null,
    provider_payment_reference text,
    purchased_report_version varchar(100),
    created_at timestamptz not null,
    confirmed_at timestamptz,
    constraint university_paid_tier check (report_tier = 'PAID'),
    constraint university_active_entitlement_complete check (
        status <> 'ACTIVE' or (purchased_report_version is not null and confirmed_at is not null)
    )
);

create table purchased_university_report_snapshot (
    entitlement_id uuid primary key
        references university_report_entitlement(entitlement_id) on delete cascade,
    assessment_id uuid not null unique references university_assessment(id) on delete cascade,
    report_version varchar(100) not null,
    frozen_at timestamptz not null,
    report_json jsonb not null
);

create table processed_university_payment_event (
    payment_provider varchar(32) not null,
    provider_event_id text not null,
    entitlement_id uuid not null
        references university_report_entitlement(entitlement_id) on delete cascade,
    processed_at timestamptz not null,
    primary key (payment_provider, provider_event_id)
);
