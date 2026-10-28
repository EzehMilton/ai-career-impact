package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.decision.university.UniversityProfileDecisionResult;

import java.util.Objects;
import java.util.Optional;

/**
 * The stored deterministic result of one University assessment, under exactly one decision policy.
 *
 * <p>A historical {@code university-decision-v1} assessment holds its five-measure decision; an assessment made with
 * {@code university-decision-v2} holds its three-measure course profile. Which one applies follows the stored policy
 * version, never the current application version, so a v1 assessment is never reinterpreted as v2.</p>
 */
public record UniversityAssessmentResult(
        Optional<UniversityDecisionResult> legacyDecision,
        Optional<UniversityProfileDecisionResult> courseProfile,
        AssessmentSource source,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public UniversityAssessmentResult {
        Objects.requireNonNull(legacyDecision, "legacyDecision must not be null");
        Objects.requireNonNull(courseProfile, "courseProfile must not be null");
        if (legacyDecision.isPresent() == courseProfile.isPresent()) {
            throw new IllegalArgumentException("a result holds either a v1 decision or a v2 course profile");
        }
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(judgmentSchemaVersion, "judgmentSchemaVersion must not be null");
        if (judgmentSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("judgmentSchemaVersion must not be blank");
        }
        judgmentSchemaVersion = judgmentSchemaVersion.strip();
        Objects.requireNonNull(decisionPolicyVersion, "decisionPolicyVersion must not be null");
        String version = legacyDecision.map(UniversityDecisionResult::policyVersion)
                .orElseGet(() -> courseProfile.orElseThrow().policyVersion());
        if (!decisionPolicyVersion.equals(version)) {
            throw new IllegalArgumentException("decisionPolicyVersion must match the decision");
        }
    }

    /** A historical {@code university-decision-v1} result. */
    public UniversityAssessmentResult(UniversityDecisionResult decision, AssessmentSource source,
            String judgmentSchemaVersion, String decisionPolicyVersion) {
        this(Optional.of(Objects.requireNonNull(decision, "decision must not be null")), Optional.empty(), source,
                judgmentSchemaVersion, decisionPolicyVersion);
    }

    /** A {@code university-decision-v2} course profile. */
    public UniversityAssessmentResult(UniversityProfileDecisionResult courseProfile, AssessmentSource source,
            String judgmentSchemaVersion, String decisionPolicyVersion) {
        this(Optional.empty(), Optional.of(Objects.requireNonNull(courseProfile, "courseProfile must not be null")),
                source, judgmentSchemaVersion, decisionPolicyVersion);
    }

    /** The v1 decision. Only a historical v1 assessment has one; callers on v2 paths use {@link #courseProfile()}. */
    public UniversityDecisionResult decision() {
        return legacyDecision.orElseThrow(() -> new IllegalStateException(
                "this assessment was decided by " + decisionPolicyVersion + " and has no v1 decision"));
    }

    /** Whether this assessment was decided by {@code university-decision-v2}. */
    public boolean usesCourseProfile() {
        return courseProfile.isPresent();
    }

    public UniversityProfileDecisionResult profile() {
        return courseProfile.orElseThrow(() -> new IllegalStateException(
                "this assessment was decided by " + decisionPolicyVersion + " and has no course profile"));
    }

    /** The headline status and confidence, whichever policy decided the assessment. */
    public AssessmentDecision headline() {
        return legacyDecision.map(UniversityDecisionResult::assessmentDecision)
                .orElseGet(() -> courseProfile.orElseThrow().assessmentDecision());
    }

    public double informationSufficiency() {
        return legacyDecision.map(UniversityDecisionResult::informationSufficiency)
                .orElseGet(() -> courseProfile.orElseThrow().informationSufficiency());
    }
}
