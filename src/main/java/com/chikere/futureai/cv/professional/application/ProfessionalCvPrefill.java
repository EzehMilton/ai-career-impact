package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;

import java.util.Objects;

public record ProfessionalCvPrefill(
        ProfessionalCvParseResult result,
        ProfessionalAssessmentInputMetadata inputMetadata) {
    public ProfessionalCvPrefill {
        Objects.requireNonNull(result);
        Objects.requireNonNull(inputMetadata);
    }
}
