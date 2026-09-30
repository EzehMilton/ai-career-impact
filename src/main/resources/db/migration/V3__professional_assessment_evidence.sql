create table professional_assessment_evidence_bundle (
    assessment_id uuid primary key references professional_assessment(id) on delete cascade,
    status varchar(32) not null,
    evidence_version varchar(100) not null,
    source varchar(32) not null,
    provider_identifier varchar(100) not null,
    retrieved_at timestamptz not null,
    constraint professional_evidence_status check (status in ('PENDING', 'AVAILABLE', 'PARTIAL', 'FAILED'))
);

create table professional_assessment_evidence_item (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment_evidence_bundle(assessment_id) on delete cascade,
    item_order integer not null,
    category varchar(48) not null,
    title text not null,
    summary text not null,
    source_name text not null,
    source_reference text,
    source_url text,
    publisher text,
    geography text,
    publication_date date,
    retrieved_at timestamptz not null,
    metric_name text,
    metric_value numeric,
    metric_unit text,
    source_quality varchar(32) not null,
    relevance_note text not null,
    constraint professional_evidence_metric_complete check (
        (metric_name is null and metric_value is null and metric_unit is null)
        or (metric_name is not null and metric_value is not null and metric_unit is not null)
    ),
    constraint professional_evidence_item_order unique (assessment_id, item_order)
);

create index professional_evidence_item_assessment_idx
    on professional_assessment_evidence_item (assessment_id, item_order);
