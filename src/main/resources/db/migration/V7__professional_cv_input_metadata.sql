alter table professional_assessment
    add column input_source varchar(32) not null default 'MANUAL',
    add column cv_original_filename text,
    add column cv_media_type text,
    add column cv_parser_version varchar(100),
    add column cv_parsed_at timestamptz;

alter table professional_assessment
    add constraint professional_assessment_cv_metadata_consistent check (
        (input_source = 'MANUAL'
            and cv_original_filename is null
            and cv_media_type is null
            and cv_parser_version is null
            and cv_parsed_at is null)
        or
        (input_source = 'CV_UPLOAD'
            and cv_original_filename is not null
            and cv_media_type is not null
            and cv_parser_version is not null
            and cv_parsed_at is not null)
    );
