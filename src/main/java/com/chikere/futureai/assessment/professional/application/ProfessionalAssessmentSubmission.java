package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;

import java.util.UUID;

public interface ProfessionalAssessmentSubmission {
    UUID submitProfessionalAssessment(ProfessionalAssessmentState state);

    UUID submitProfessionalAssessment(ProfessionalAssessmentState state,
            ProfessionalAssessmentInputMetadata inputMetadata);
}
