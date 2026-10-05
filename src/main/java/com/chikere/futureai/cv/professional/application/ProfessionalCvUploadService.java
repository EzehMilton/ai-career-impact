package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ProfessionalCvUploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalCvUploadService.class);
    private final ProfessionalCvParser parser;
    private final Optional<ProfessionalProfileExtractor> profileExtractor;
    private final ProfessionalProfileValidator profileValidator;
    private final Clock clock;

    public ProfessionalCvUploadService(ProfessionalCvParser parser,
            Optional<ProfessionalProfileExtractor> profileExtractor,
            ProfessionalProfileValidator profileValidator, Clock clock) {
        this.parser = Objects.requireNonNull(parser);
        this.profileExtractor = Objects.requireNonNull(profileExtractor);
        this.profileValidator = Objects.requireNonNull(profileValidator);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProfessionalCvProfileResult extractProfile(ProfessionalCvDocument document) {
        var parsed = parser.parse(document);
        LOGGER.info("Professional CV parsed fileType={} pageCount={} outcome=SUCCESS", document.type(),
                parsed.pageCount().isPresent() ? parsed.pageCount().getAsInt() : "unavailable");
        if (parsed.text().strip().length() < 80) {
            throw new ProfessionalProfileExtractionException("CV contained insufficient extractable text");
        }
        ProfessionalProfileExtractor extractor = profileExtractor.orElseThrow(() ->
                new ProfessionalProfileExtractionException("Profile extraction is unavailable"));
        ProfessionalProfile profile;
        try {
            profile = profileValidator.validate(extractor.extract(parsed.text()));
        }
        catch (ProfessionalProfileExtractionException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw new ProfessionalProfileExtractionException("Profile extraction failed", exception);
        }
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload(document.originalFilename(),
                document.type().mediaType(), parsed.parserVersion(), Instant.now(clock));
        return new ProfessionalCvProfileResult(profile, metadata);
    }
}
