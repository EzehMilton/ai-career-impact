-- Stage A3: the immutable a-level-report-v2 detailed report of an a-level-decision-v2 subject profile. Historical
-- a-level-report-v1 purchases keep their frozen document in purchased_a_level_report_snapshot and their stored PDF
-- bytes, and have no row here. A v2 purchase records only a marker there; its report is stored here once prepared.

-- Every source version as a column, the composed report (subject profile, deterministic guidance, narrative and any
-- evidence) as one document. Inserted once, never updated, and only with an AVAILABLE or final FALLBACK narrative:
-- a retryable narrative failure stores nothing. No PDF bytes: the v2 PDF is rendered on demand from this document.
create table a_level_detailed_report (
    id uuid primary key,
    assessment_id uuid not null references a_level_assessment(id),
    entitlement_id uuid not null references a_level_report_entitlement(entitlement_id),
    judgment_schema_version varchar(100) not null,
    decision_policy_version varchar(100) not null,
    guidance_policy_version varchar(100) not null,
    paid_narrative_version varchar(100) not null,
    paid_narrative_status varchar(24) not null,
    evidence_version varchar(100),
    report_version varchar(100) not null,
    pdf_version varchar(100) not null,
    generated_at timestamptz not null,
    report jsonb not null,
    constraint uq_a_level_detailed_report_assessment unique (assessment_id),
    constraint uq_a_level_detailed_report_entitlement unique (entitlement_id),
    constraint a_level_detailed_report_narrative_status check (paid_narrative_status in ('AVAILABLE', 'FALLBACK'))
);
