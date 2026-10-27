-- Stage 4F: the paid narrative, the assembled professional-report-v3 snapshot and its stored professional-report-pdf-v5.
-- All three are inserted once and never updated. professional-report-v2 snapshots and their PDFs are untouched.

-- One paid narrative outcome per assessment, tied to the analysis and guidance it explains. A FALLBACK row carries
-- no narrative text and records why; the report then uses deterministic copy.
create table professional_paid_narrative (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment(id),
    analysis_id uuid not null references professional_responsibility_analysis(id),
    guidance_id uuid not null references professional_responsibility_guidance(id),
    narrative_version varchar(100) not null,
    status varchar(24) not null,
    provider varchar(64) not null,
    model text,
    provider_request_id text,
    attempts integer not null,
    fallback_reason text,
    generated_at timestamptz not null,
    narrative jsonb,
    constraint uq_professional_paid_narrative_assessment unique (assessment_id),
    constraint uq_professional_paid_narrative_guidance unique (guidance_id),
    constraint professional_paid_narrative_status check (status in ('AVAILABLE', 'FALLBACK')),
    constraint professional_paid_narrative_text check ((status = 'AVAILABLE') = (narrative is not null)),
    constraint professional_paid_narrative_fallback_reason check (status = 'AVAILABLE' or fallback_reason is not null),
    constraint professional_paid_narrative_attempts check (attempts between 0 and 2)
);

-- The assembled detailed report: every source artefact and version as columns, the composed content as one document.
create table professional_detailed_report (
    id uuid primary key,
    assessment_id uuid not null references professional_assessment(id),
    entitlement_id uuid not null references professional_report_entitlement(entitlement_id),
    role_report_version varchar(100) not null,
    decision_policy_version varchar(100) not null,
    analysis_id uuid not null references professional_responsibility_analysis(id),
    responsibility_schema_version varchar(100) not null,
    responsibility_policy_version varchar(100) not null,
    guidance_id uuid not null references professional_responsibility_guidance(id),
    guidance_policy_version varchar(100) not null,
    paid_narrative_id uuid not null references professional_paid_narrative(id),
    paid_narrative_version varchar(100) not null,
    paid_narrative_status varchar(24) not null,
    report_version varchar(100) not null,
    pdf_version varchar(100) not null,
    generated_at timestamptz not null,
    report jsonb not null,
    constraint uq_professional_detailed_report_assessment unique (assessment_id),
    constraint uq_professional_detailed_report_entitlement unique (entitlement_id)
);

-- The PDF of one detailed report, rendered once from the stored report and kept as bytes.
create table professional_detailed_report_pdf (
    report_id uuid primary key references professional_detailed_report(id),
    assessment_id uuid not null unique references professional_assessment(id),
    report_version varchar(100) not null,
    pdf_version varchar(100) not null,
    generated_at timestamptz not null,
    filename varchar(180) not null,
    file_size bigint not null,
    sha256 char(64) not null,
    pdf_bytes bytea not null,
    constraint professional_detailed_report_pdf_size_positive check (file_size > 0),
    constraint professional_detailed_report_pdf_size_matches check (file_size = octet_length(pdf_bytes)),
    constraint professional_detailed_report_pdf_sha256_format check (sha256 ~ '^[0-9a-f]{64}$')
);
