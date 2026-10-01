package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Optional;

/**
 * The university application port for atomic judgments.
 *
 * <p>It states the capability the use case needs without TypeSafe, Jev, Spring AI or HTTP types.</p>
 */
public interface UniversityJudgmentProvider {

    AssessmentJudgmentSet judge(UniversityAssessmentState state);

    AssessmentSource source();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
