create table a_level_assessment (
    id uuid primary key,
    created_at timestamptz not null,
    selected_subjects jsonb not null,
    predicted_grades jsonb not null,
    strengths_and_interests jsonb not null,
    preferred_degree_areas jsonb not null,
    preferred_career_areas jsonb not null,
    country text,
    additional_context text,
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
    key_strengths jsonb not null,
    development_opportunities jsonb not null,
    constraint a_level_assessment_journey check (
        judgment_journey = 'A_LEVEL' and decision_journey = 'A_LEVEL'
    )
);

create table a_level_assessment_judgment (
    assessment_id uuid not null references a_level_assessment(id) on delete cascade,
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

create table a_level_assessment_measure (
    assessment_id uuid not null references a_level_assessment(id) on delete cascade,
    measure varchar(64) not null,
    indicator double precision not null,
    band varchar(32) not null,
    confidence double precision not null,
    primary key (assessment_id, measure)
);

create table a_level_assessment_factor (
    assessment_id uuid not null references a_level_assessment(id) on delete cascade,
    factor_type varchar(32) not null,
    position integer not null,
    dimension_id varchar(100) not null,
    effect varchar(64) not null,
    direction varchar(100) not null,
    score double precision not null,
    optionality_component double precision not null,
    weight double precision not null,
    signed_effect double precision not null,
    primary key (assessment_id, factor_type, position),
    constraint a_level_factor_type check (factor_type in ('STRENGTH', 'DEVELOPMENT'))
);
