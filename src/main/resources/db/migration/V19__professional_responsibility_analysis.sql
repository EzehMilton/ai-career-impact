-- Paid Professional responsibility analysis (Stage 4D). One immutable completed snapshot per assessment: the exact
-- inputs sent, every provider judgment with its distribution, and the deterministic classification made at the time.
-- Nothing here is recomputed later, and nothing changes the role-level assessment it belongs to.

create table professional_responsibility_analysis (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment(id),
    -- Only completed snapshots are stored here; failed attempts are kept in their own table below.
    status varchar(16) not null check (status = 'COMPLETED'),
    schema_version varchar(100) not null,
    policy_version varchar(100) not null,
    judgment_source varchar(32) not null,
    model text,
    provider_request_id text,
    input_tokens bigint,
    output_tokens bigint,
    profession text not null,
    work_description text not null,
    skills jsonb not null,
    regulated_context_value double precision not null,
    regulated_context_level varchar(16) not null,
    regulated_context_confidence double precision,
    regulated_context_distribution jsonb not null,
    regulated_context_nearest_level integer,
    regulated_context_nearest_label text,
    policy_parameters jsonb not null,
    created_at timestamptz not null,
    completed_at timestamptz not null,
    -- The final guard against two instances completing the same analysis.
    constraint uq_professional_responsibility_analysis_assessment unique (assessment_id)
);

create table professional_responsibility_analysis_item (
    analysis_id uuid not null references professional_responsibility_analysis(id),
    responsibility_index integer not null check (responsibility_index between 1 and 8),
    responsibility_key varchar(8) not null,
    responsibility_text text not null,
    described_clearly double precision not null,
    category varchar(64) not null,
    display_label text not null,
    needs_close_human_oversight boolean not null,
    oversight_rule varchar(100),
    classification_rule varchar(100) not null,
    close_call boolean not null,
    primary key (analysis_id, responsibility_index),
    unique (analysis_id, responsibility_key),
    check (needs_close_human_oversight = (oversight_rule is not null))
);

create table professional_responsibility_analysis_judgment (
    analysis_id uuid not null,
    responsibility_index integer not null,
    judgment varchar(64) not null,
    judgment_value double precision not null,
    judgment_level varchar(16) not null,
    provider_confidence double precision,
    distribution jsonb not null,
    nearest_level integer,
    nearest_label text,
    primary key (analysis_id, responsibility_index, judgment),
    foreign key (analysis_id, responsibility_index)
        references professional_responsibility_analysis_item(analysis_id, responsibility_index)
        -- Checked at commit: the item and judgment rows of one snapshot are inserted in a single transaction.
        deferrable initially deferred
);

-- Failed attempts, for diagnosis and so a retry is always possible. Never read as a result.
create table professional_responsibility_analysis_failure (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment(id),
    failed_at timestamptz not null,
    stage varchar(32) not null,
    error_type text not null,
    error_message text,
    schema_version varchar(100) not null,
    policy_version varchar(100) not null
);

create index idx_professional_responsibility_analysis_failure_assessment
    on professional_responsibility_analysis_failure(assessment_id);
