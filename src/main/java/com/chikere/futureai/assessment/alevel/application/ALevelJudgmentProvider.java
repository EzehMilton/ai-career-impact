package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Optional;

/**
 * The A-level application port for {@code a-level-v2} atomic judgments of a subject combination.
 *
 * <p>It receives only {@link ALevelJudgmentInput}, never the student's state, and states the capability the use case
 * needs without TypeSafe, Jev, Spring AI or HTTP types. Historical {@code a-level-v1} judgments are already stored
 * and are never requested again.</p>
 */
public interface ALevelJudgmentProvider {

    AssessmentJudgmentSet judge(ALevelJudgmentInput input);

    AssessmentSource source();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }

    /** The configured fixture, for a local fixture provider; used only to label diagnostic traces. */
    default Optional<String> fixtureId() {
        return Optional.empty();
    }
}
