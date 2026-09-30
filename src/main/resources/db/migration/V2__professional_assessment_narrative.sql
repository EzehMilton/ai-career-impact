create table professional_assessment_narrative (
    assessment_id uuid primary key references professional_assessment(id) on delete cascade,
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
    exposure_explanation text,
    human_advantage_explanation text,
    complementarity_explanation text,
    adaptability_explanation text,
    practical_actions jsonb,
    caution_or_limitations text,
    constraint professional_narrative_lifecycle check (
        (status = 'AVAILABLE'
            and generated_at is not null
            and headline is not null
            and summary is not null
            and what_this_means is not null
            and exposure_explanation is not null
            and human_advantage_explanation is not null
            and complementarity_explanation is not null
            and adaptability_explanation is not null
            and practical_actions is not null
            and caution_or_limitations is not null)
        or
        (status in ('PENDING', 'FAILED')
            and generated_at is null
            and headline is null
            and summary is null
            and what_this_means is null
            and exposure_explanation is null
            and human_advantage_explanation is null
            and complementarity_explanation is null
            and adaptability_explanation is null
            and practical_actions is null
            and caution_or_limitations is null)
    )
);
