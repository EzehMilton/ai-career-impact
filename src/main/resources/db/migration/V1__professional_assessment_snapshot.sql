create table professional_assessment (
    id uuid primary key,
    created_at timestamptz not null,
    profession text not null,
    work_description text not null,
    responsibilities jsonb not null,
    skills jsonb not null,
    years_of_experience integer,
    source varchar(32) not null,
    model_identifier text,
    judgment_schema_version varchar(100) not null,
    decision_policy_version varchar(100) not null,
    judgment_journey varchar(32) not null,
    information_sufficiency double precision not null,
    decision_journey varchar(32) not null,
    overall_indicator double precision,
    result_label text not null,
    assessment_confidence varchar(32) not null,
    assessment_status varchar(40) not null,
    key_drivers jsonb not null,
    decision_protective_factors jsonb not null
);

create table professional_assessment_judgment (
    assessment_id uuid not null references professional_assessment(id),
    position integer not null,
    dimension_id varchar(100) not null,
    dimension_name text not null,
    numeric_value double precision,
    categorical_value text,
    confidence varchar(32),
    confidence_value double precision,
    probability_distribution jsonb not null,
    primary key (assessment_id, position),
    unique (assessment_id, dimension_id)
);

create table professional_assessment_measure (
    assessment_id uuid not null references professional_assessment(id),
    measure varchar(64) not null,
    indicator double precision not null,
    band varchar(32) not null,
    confidence double precision not null,
    primary key (assessment_id, measure)
);

create table professional_assessment_factor (
    assessment_id uuid not null references professional_assessment(id),
    factor_type varchar(16) not null,
    position integer not null,
    dimension_id varchar(100) not null,
    effect varchar(64) not null,
    direction varchar(100) not null,
    score double precision not null,
    exposure_component double precision not null,
    weight double precision not null,
    signed_effect double precision not null,
    primary key (assessment_id, factor_type, position)
);
