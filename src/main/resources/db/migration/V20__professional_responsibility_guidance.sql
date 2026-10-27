-- Deterministic paid guidance (Stage 4E), derived once from a stored responsibility analysis and frozen. The policy
-- version and the analysis it came from are columns; the structured guidance is one immutable JSON document.
create table professional_responsibility_guidance (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment(id),
    analysis_id uuid not null references professional_responsibility_analysis(id),
    policy_version varchar(100) not null,
    created_at timestamptz not null,
    guidance jsonb not null,
    constraint uq_professional_responsibility_guidance_assessment unique (assessment_id),
    constraint uq_professional_responsibility_guidance_analysis unique (analysis_id)
);
