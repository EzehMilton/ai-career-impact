alter table professional_assessment
    add column cv_extractor_version varchar(100);

-- Every CV assessment saved before this migration used the v1 profile extractor.
update professional_assessment
   set cv_extractor_version = 'professional-cv-extractor-v1'
 where input_source = 'CV_UPLOAD';

alter table professional_assessment
    drop constraint professional_assessment_cv_metadata_consistent;

alter table professional_assessment
    add constraint professional_assessment_cv_metadata_consistent check (
        (input_source = 'MANUAL'
            and cv_original_filename is null
            and cv_media_type is null
            and cv_parser_version is null
            and cv_extractor_version is null
            and cv_parsed_at is null)
        or
        (input_source = 'CV_UPLOAD'
            and cv_original_filename is not null
            and cv_media_type is not null
            and cv_parser_version is not null
            and cv_extractor_version is not null
            and cv_parsed_at is not null)
    );
