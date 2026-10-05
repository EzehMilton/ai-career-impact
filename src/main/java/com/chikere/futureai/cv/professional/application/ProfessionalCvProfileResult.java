package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;

import java.util.Objects;

public record ProfessionalCvProfileResult(
        ProfessionalProfile profile,
        ProfessionalAssessmentInputMetadata inputMetadata) {
    public ProfessionalCvProfileResult {
        Objects.requireNonNull(profile);
        Objects.requireNonNull(inputMetadata);
    }
}
