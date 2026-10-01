create table a_level_assessment_narrative (
    assessment_id uuid primary key references a_level_assessment(id) on delete cascade,
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
    future_optionality_explanation text,
    strengths_explanation text,
    degree_pathway_explanation text,
    analytical_foundation_explanation text,
    human_and_creative_explanation text,
    transferability_explanation text,
    adaptability_explanation text,
    development_opportunities_explanation text,
    practical_actions jsonb,
    caution_or_limitations text,
    constraint a_level_narrative_status check (status in ('PENDING', 'AVAILABLE', 'FAILED')),
    constraint a_level_narrative_lifecycle check (
        (status = 'AVAILABLE'
            and generated_at is not null
            and headline is not null
            and summary is not null
            and what_this_means is not null
            and future_optionality_explanation is not null
            and strengths_explanation is not null
            and degree_pathway_explanation is not null
            and analytical_foundation_explanation is not null
            and human_and_creative_explanation is not null
            and transferability_explanation is not null
            and adaptability_explanation is not null
            and development_opportunities_explanation is not null
            and practical_actions is not null
            and caution_or_limitations is not null)
        or
        (status in ('PENDING', 'FAILED')
            and generated_at is null
            and headline is null
            and summary is null
            and what_this_means is null
            and future_optionality_explanation is null
            and strengths_explanation is null
            and degree_pathway_explanation is null
            and analytical_foundation_explanation is null
            and human_and_creative_explanation is null
            and transferability_explanation is null
            and adaptability_explanation is null
            and development_opportunities_explanation is null
            and practical_actions is null
            and caution_or_limitations is null)
    )
);
