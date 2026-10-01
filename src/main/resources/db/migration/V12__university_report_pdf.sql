create table purchased_university_report_pdf (
    entitlement_id uuid primary key
        references university_report_entitlement(entitlement_id) on delete cascade,
    assessment_id uuid not null unique references university_assessment(id) on delete cascade,
    purchased_report_version varchar(100) not null,
    pdf_template_version varchar(100) not null,
    generated_at timestamptz not null,
    filename varchar(180) not null,
    file_size bigint not null,
    sha256 char(64) not null,
    pdf_bytes bytea not null,
    constraint university_report_pdf_size_positive check (file_size > 0),
    constraint university_report_pdf_size_matches check (file_size = octet_length(pdf_bytes)),
    constraint university_report_pdf_sha256_format check (sha256 ~ '^[0-9a-f]{64}$')
);
