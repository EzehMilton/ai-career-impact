create table university_assessment_narrative (
    assessment_id uuid primary key references university_assessment(id) on delete cascade,
    status varchar(32) not null,
    source varchar(32) not null,
    provider_identifier varchar(100) not null,
    model_identifier text,
    prompt_schema_version varchar(100) not null,
    attempted_at timestamptz not null,
    generated_at timestamptz,
    headline text,
    summary text,
    what_this_means text,
    future_readiness_explanation text,
    strengths_explanation text,
    development_opportunities_explanation text,
    complementarity_explanation text,
    transferable_skills_explanation text,
    career_adaptability_explanation text,
    practical_actions jsonb,
    caution_or_limitations text,
    constraint university_narrative_status check (status in ('PENDING', 'AVAILABLE', 'FAILED')),
    constraint university_narrative_lifecycle check (
        (status = 'AVAILABLE'
            and generated_at is not null
            and headline is not null
            and summary is not null
            and what_this_means is not null
            and future_readiness_explanation is not null
            and strengths_explanation is not null
            and development_opportunities_explanation is not null
            and complementarity_explanation is not null
            and transferable_skills_explanation is not null
            and career_adaptability_explanation is not null
            and practical_actions is not null
            and caution_or_limitations is not null)
        or
        (status in ('PENDING', 'FAILED')
            and generated_at is null
            and headline is null
            and summary is null
            and what_this_means is null
            and future_readiness_explanation is null
            and strengths_explanation is null
            and development_opportunities_explanation is null
            and complementarity_explanation is null
            and transferable_skills_explanation is null
            and career_adaptability_explanation is null
            and practical_actions is null
            and caution_or_limitations is null)
    )
);
