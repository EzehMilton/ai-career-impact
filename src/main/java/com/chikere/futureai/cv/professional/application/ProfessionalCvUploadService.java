package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ProfessionalCvUploadService implements ProfessionalCvAnalysisUseCase {
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

    @Override
    public ProfessionalCvProfileResult extractProfile(ProfessionalCvDocument document) {
        ProfessionalCvText parsed;
        try {
            parsed = parser.parse(document);
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Professional CV parsed fileType={} outcome=FAILED failureType={}",
                    document.type(), exception.getClass().getSimpleName());
            throw exception;
        }
        LOGGER.info("Professional CV parsed fileType={} pageCount={} outcome=SUCCESS", document.type(),
                parsed.pageCount().isPresent() ? parsed.pageCount().getAsInt() : "unavailable");
        if (parsed.text().strip().length() < 80) {
            LOGGER.warn("Professional profile extraction rejected by validation fileType={} "
                    + "outcome=REJECTED reason=INSUFFICIENT_EXTRACTABLE_TEXT", document.type());
            throw new ProfessionalProfileExtractionException("CV contained insufficient extractable text");
        }
        ProfessionalProfileExtractor extractor = profileExtractor.orElse(null);
        if (extractor == null) {
            LOGGER.warn("Professional profile extraction failed fileType={} outcome=FAILED "
                    + "reason=EXTRACTOR_UNAVAILABLE", document.type());
            throw new ProfessionalProfileExtractionException("Profile extraction is unavailable");
        }
        long started = System.nanoTime();
        LOGGER.info("Professional profile extraction started fileType={}", document.type());
        ProfessionalProfile candidate;
        try {
            candidate = extractor.extract(parsed.text());
        }
        catch (ProfessionalProfileExtractionException exception) {
            LOGGER.warn("Professional profile extraction failed fileType={} outcome=FAILED "
                            + "failureType={} latencyMs={}", document.type(),
                    exception.getClass().getSimpleName(), elapsedMilliseconds(started));
            throw exception;
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Professional profile extraction failed fileType={} outcome=FAILED "
                            + "failureType={} latencyMs={}", document.type(),
                    exception.getClass().getSimpleName(), elapsedMilliseconds(started));
            throw new ProfessionalProfileExtractionException("Profile extraction failed", exception);
        }
        ProfessionalProfile profile;
        try {
            profile = profileValidator.validate(candidate);
        }
        catch (ProfessionalProfileExtractionException exception) {
            LOGGER.warn("Professional profile extraction rejected by validation fileType={} "
                            + "outcome=REJECTED failureType={} latencyMs={}", document.type(),
                    exception.getClass().getSimpleName(), elapsedMilliseconds(started));
            throw exception;
        }
        LOGGER.info("Professional profile extraction succeeded fileType={} outcome=SUCCESS latencyMs={}",
                document.type(), elapsedMilliseconds(started));
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload(document.originalFilename(),
                document.type().mediaType(), parsed.parserVersion(), extractor.version(), Instant.now(clock));
        return new ProfessionalCvProfileResult(profile, metadata);
    }

    private long elapsedMilliseconds(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
