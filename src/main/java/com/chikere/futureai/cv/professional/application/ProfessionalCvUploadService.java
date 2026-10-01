package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class ProfessionalCvUploadService {
    private final ProfessionalCvParser parser;
    private final Clock clock;

    public ProfessionalCvUploadService(ProfessionalCvParser parser, Clock clock) {
        this.parser = Objects.requireNonNull(parser);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProfessionalCvPrefill parse(ProfessionalCvDocument document) {
        var result = parser.parse(document);
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload(document.originalFilename(),
                document.type().mediaType(), result.parserVersion(), Instant.now(clock));
        return new ProfessionalCvPrefill(result, metadata);
    }
}
