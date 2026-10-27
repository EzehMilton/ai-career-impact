package com.chikere.futureai.responsibility.professional.policy;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;

/** Deterministic classification of responsibility judgments. {@link ProfessionalResponsibilityPolicyV1} is v1. */
public interface ProfessionalResponsibilityClassifier {

    ProfessionalResponsibilityClassificationResult classify(ProfessionalResponsibilityJudgmentResult judgments);
}
