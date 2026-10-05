package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;

import java.util.UUID;

public interface ProfessionalAssessmentUseCase {
    UUID submit(ProfessionalAssessmentState state);

    UUID submit(ProfessionalAssessmentState state, ProfessionalAssessmentInputMetadata inputMetadata);
}
