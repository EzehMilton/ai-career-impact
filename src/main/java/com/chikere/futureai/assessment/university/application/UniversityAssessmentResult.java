package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.decision.university.UniversityDecisionResult;

import java.util.Objects;

public record UniversityAssessmentResult(
        UniversityDecisionResult decision,
        AssessmentSource source,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public UniversityAssessmentResult {
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
