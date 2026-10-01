package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Optional;

/**
 * The A-level application port for atomic judgments.
 *
 * <p>It states the capability the use case needs without TypeSafe, Jev, Spring AI or HTTP types.</p>
 */
public interface ALevelJudgmentProvider {

    AssessmentJudgmentSet judge(ALevelAssessmentState state);

    AssessmentSource source();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
