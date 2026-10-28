-- Paid University module analysis (Stage 5E). One immutable completed snapshot per assessment: the exact course
-- context and modules sent, every provider judgment with its distribution, and the deterministic classification made
-- at the time. Nothing here is recomputed later, and nothing changes the course-level assessment it belongs to.

create table university_module_analysis (
    id uuid primary key,
    assessment_id uuid not null references university_assessment(id),
    -- Only completed snapshots are stored here; failed attempts are kept in their own table below.
    status varchar(16) not null check (status = 'COMPLETED'),
    schema_version varchar(100) not null,
    policy_version varchar(100) not null,
    judgment_source varchar(32) not null,
    model text,
    provider_request_id text,
    input_tokens bigint,
    output_tokens bigint,
    course_title text not null,
    course_description text not null,
    skills_developed jsonb not null,
    selected_module_count integer not null check (selected_module_count between 1 and 8),
    created_at timestamptz not null,
    completed_at timestamptz not null,
    -- The final guard against two instances completing the same analysis.
    constraint uq_university_module_analysis_assessment unique (assessment_id)
);

create table university_module_analysis_item (
    analysis_id uuid not null references university_module_analysis(id),
    -- Position in the Jev request (m1..m8), which is course order.
    request_index integer not null check (request_index between 1 and 8),
    module_key varchar(8) not null,
    -- The module's 1-based position in the course's stored module list.
    module_id integer not null check (module_id >= 1),
    module_title text not null,
    category varchar(64) not null,
    display_label text not null,
    classification_rule varchar(64) not null,
    dominant_value double precision not null,
    competing_value double precision not null,
    dominance_gap double precision not null,
    independent_demonstration_sensitive boolean not null,
    professional_standards_relevant boolean not null,
    close_call boolean not null,
    close_call_reasons jsonb not null,
    primary key (analysis_id, request_index),
    unique (analysis_id, module_key),
    unique (analysis_id, module_id),
    check (close_call = (jsonb_array_length(close_call_reasons) > 0))
);

-- Every judgment of every analysed module. Truth judgments (described_clearly, professional_standards) have no
-- distribution or confidence; scored judgments keep the provider's probabilities exactly.
create table university_module_analysis_judgment (
    analysis_id uuid not null,
    request_index integer not null,
    judgment varchar(64) not null,
    judgment_value double precision not null,
    provider_confidence double precision,
    distribution jsonb not null,
    nearest_level integer,
    nearest_label text,
    primary key (analysis_id, request_index, judgment),
    foreign key (analysis_id, request_index)
        references university_module_analysis_item(analysis_id, request_index)
        -- Checked at commit: the item and judgment rows of one snapshot are inserted in a single transaction.
        deferrable initially deferred
);

-- Failed attempts, for diagnosis and so a retry is always possible. Never read as a result.
create table university_module_analysis_failure (
    id uuid primary key,
    assessment_id uuid not null references university_assessment(id),
    failed_at timestamptz not null,
    category varchar(32) not null,
    retryable boolean not null,
    error_type text not null,
    error_message text,
    schema_version varchar(100) not null,
    policy_version varchar(100) not null
);

create index idx_university_module_analysis_failure_assessment
    on university_module_analysis_failure(assessment_id);
