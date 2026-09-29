package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;

import java.util.Objects;

public record ProfessionalAssessmentResult(
        ProfessionalDecisionResult decision,
        AssessmentSource source,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public ProfessionalAssessmentResult {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(judgmentSchemaVersion, "judgmentSchemaVersion must not be null");
        if (judgmentSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("judgmentSchemaVersion must not be blank");
        }
        judgmentSchemaVersion = judgmentSchemaVersion.strip();
        Objects.requireNonNull(decisionPolicyVersion, "decisionPolicyVersion must not be null");
        if (!decisionPolicyVersion.equals(decision.policyVersion())) {
            throw new IllegalArgumentException("decisionPolicyVersion must match the decision");
        }
    }
}
