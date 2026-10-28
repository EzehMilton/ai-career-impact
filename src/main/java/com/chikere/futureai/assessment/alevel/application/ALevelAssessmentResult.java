package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * The stored deterministic result of one A-level assessment, under exactly one decision policy.
 *
 * <p>A historical {@code a-level-decision-v1} assessment holds its six-measure decision; an assessment made with
 * {@code a-level-decision-v2} holds its subject profile. Which one applies follows the stored policy version, never
 * the current application version, so a v1 assessment is never reinterpreted as v2.</p>
 */
public record ALevelAssessmentResult(
        Optional<ALevelDecisionResult> legacyDecision,
        Optional<ALevelProfileDecisionResult> subjectProfile,
        AssessmentSource source,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public ALevelAssessmentResult {
        Objects.requireNonNull(legacyDecision, "legacyDecision must not be null");
        Objects.requireNonNull(subjectProfile, "subjectProfile must not be null");
        if (legacyDecision.isPresent() == subjectProfile.isPresent()) {
            throw new IllegalArgumentException("a result holds either a v1 decision or a v2 subject profile");
        }
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(judgmentSchemaVersion, "judgmentSchemaVersion must not be null");
        if (judgmentSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("judgmentSchemaVersion must not be blank");
        }
        judgmentSchemaVersion = judgmentSchemaVersion.strip();
        Objects.requireNonNull(decisionPolicyVersion, "decisionPolicyVersion must not be null");
        String version = legacyDecision.map(ALevelDecisionResult::policyVersion)
                .orElseGet(() -> subjectProfile.orElseThrow().policyVersion());
        if (!decisionPolicyVersion.equals(version)) {
            throw new IllegalArgumentException("decisionPolicyVersion must match the decision");
        }
    }

    /** A historical {@code a-level-decision-v1} result. */
    public ALevelAssessmentResult(ALevelDecisionResult decision, AssessmentSource source,
            String judgmentSchemaVersion, String decisionPolicyVersion) {
        this(Optional.of(Objects.requireNonNull(decision, "decision must not be null")), Optional.empty(), source,
                judgmentSchemaVersion, decisionPolicyVersion);
    }

    /** An {@code a-level-decision-v2} subject profile. */
    public ALevelAssessmentResult(ALevelProfileDecisionResult subjectProfile, AssessmentSource source,
            String judgmentSchemaVersion, String decisionPolicyVersion) {
        this(Optional.empty(), Optional.of(Objects.requireNonNull(subjectProfile, "subjectProfile must not be null")),
                source, judgmentSchemaVersion, decisionPolicyVersion);
    }

    /** The v1 decision. Only a historical v1 assessment has one; callers on v2 paths use {@link #profile()}. */
    public ALevelDecisionResult decision() {
        return legacyDecision.orElseThrow(() -> new IllegalStateException(
                "this assessment was decided by " + decisionPolicyVersion + " and has no v1 decision"));
    }

    /** Whether this assessment was decided by {@code a-level-decision-v2}. */
    public boolean usesSubjectProfile() {
        return subjectProfile.isPresent();
    }

    public ALevelProfileDecisionResult profile() {
        return subjectProfile.orElseThrow(() -> new IllegalStateException(
                "this assessment was decided by " + decisionPolicyVersion + " and has no subject profile"));
    }

    /** The headline status and confidence, whichever policy decided the assessment. */
    public AssessmentDecision headline() {
        return legacyDecision.map(ALevelDecisionResult::assessmentDecision)
                .orElseGet(() -> subjectProfile.orElseThrow().assessmentDecision());
    }

    public double informationSufficiency() {
        return legacyDecision.map(ALevelDecisionResult::informationSufficiency)
                .orElseGet(() -> subjectProfile.orElseThrow().informationSufficiency());
    }
}
