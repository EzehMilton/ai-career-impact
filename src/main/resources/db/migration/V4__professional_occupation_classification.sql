create table professional_occupation_classification (
    assessment_id uuid primary key references professional_assessment(id) on delete cascade,
    canonical_occupation text not null,
    classification_system varchar(32) not null,
    classification_code varchar(32) not null,
    source varchar(32) not null,
    quality varchar(48) not null,
    classifier_identifier varchar(100) not null,
    classifier_version varchar(100) not null,
    classified_at timestamptz not null
);
