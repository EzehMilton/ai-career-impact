-- Stage 5G: the university-decision-v2 course profile of new assessments, and the immutable university-report-v2
-- snapshot. Historical university-decision-v1 rows and university-report-v1 purchases are untouched: a v1 assessment
-- keeps its five measures in university_assessment_measure and has no rows here.

-- The three v2 measures of an assessment decided by university-decision-v2.
create table university_assessment_profile_measure (
    assessment_id uuid not null references university_assessment(id) on delete cascade,
    measure varchar(64) not null,
    indicator double precision not null,
    band varchar(16) not null,
    confidence double precision not null,
    lower_confidence boolean not null,
    primary key (assessment_id, measure),
    constraint university_profile_band check (band in ('LOW', 'MODERATE', 'HIGH'))
);

-- Every signed contribution of each v2 measure, in its stored order; drivers and limits are flagged subsets.
create table university_assessment_profile_factor (
    assessment_id uuid not null references university_assessment(id) on delete cascade,
    measure varchar(64) not null,
    position integer not null,
    dimension_id varchar(100) not null,
    score double precision not null,
    weight double precision not null,
    effect double precision not null,
    driver_position integer,
    limiting_position integer,
    primary key (assessment_id, measure, position),
    constraint university_profile_factor_role check (driver_position is null or limiting_position is null)
);

-- The assembled paid report of a v2 assessment: every source artefact and version as columns, the composed content
-- (course profile, modules, guidance, narrative) as one document. Inserted once, never updated. It is stored only with
-- an AVAILABLE or final FALLBACK narrative; a retryable narrative failure stores nothing.
create table university_detailed_report (
    id uuid primary key,
    assessment_id uuid not null references university_assessment(id),
    entitlement_id uuid not null references university_report_entitlement(entitlement_id),
    decision_policy_version varchar(100) not null,
    module_analysis_id uuid not null references university_module_analysis(id),
    module_schema_version varchar(100) not null,
    module_policy_version varchar(100) not null,
    guidance_policy_version varchar(100) not null,
    paid_narrative_version varchar(100) not null,
    paid_narrative_status varchar(24) not null,
    report_version varchar(100) not null,
    pdf_version varchar(100) not null,
    generated_at timestamptz not null,
    report jsonb not null,
    constraint uq_university_detailed_report_assessment unique (assessment_id),
    constraint uq_university_detailed_report_entitlement unique (entitlement_id),
    constraint university_detailed_report_narrative_status check (paid_narrative_status in ('AVAILABLE', 'FALLBACK'))
);
