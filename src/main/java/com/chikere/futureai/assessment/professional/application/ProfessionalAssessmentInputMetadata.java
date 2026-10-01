package com.chikere.futureai.assessment.professional.application;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ProfessionalAssessmentInputMetadata(
        ProfessionalInputSource source,
        Optional<String> originalFilename,
        Optional<String> mediaType,
        Optional<String> parserVersion,
        Optional<Instant> parsedAt) implements Serializable {

    public ProfessionalAssessmentInputMetadata {
        source = Objects.requireNonNull(source);
        originalFilename = Objects.requireNonNull(originalFilename);
        mediaType = Objects.requireNonNull(mediaType);
        parserVersion = Objects.requireNonNull(parserVersion);
        parsedAt = Objects.requireNonNull(parsedAt);
        if (source == ProfessionalInputSource.MANUAL
                && (originalFilename.isPresent() || mediaType.isPresent()
                || parserVersion.isPresent() || parsedAt.isPresent())) {
            throw new IllegalArgumentException("manual input must not contain CV metadata");
        }
        if (source == ProfessionalInputSource.CV_UPLOAD
                && (originalFilename.isEmpty() || mediaType.isEmpty()
                || parserVersion.isEmpty() || parsedAt.isEmpty())) {
            throw new IllegalArgumentException("CV upload metadata must be complete");
        }
    }

    public static ProfessionalAssessmentInputMetadata manual() {
        return new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.MANUAL,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static ProfessionalAssessmentInputMetadata cvUpload(
            String filename, String mediaType, String parserVersion, Instant parsedAt) {
        return new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.CV_UPLOAD,
                Optional.of(filename), Optional.of(mediaType), Optional.of(parserVersion), Optional.of(parsedAt));
    }
}
