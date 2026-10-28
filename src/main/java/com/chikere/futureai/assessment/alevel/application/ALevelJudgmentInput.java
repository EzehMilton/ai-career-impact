package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.List;
import java.util.Objects;

/**
 * Everything an {@code a-level-v2} judgment may see: the canonical subject names, and nothing about the student.
 *
 * <p>Predicted grades, strengths and interests, preferred degree and career areas, country and free-text context
 * describe the student rather than the subject combination. They stay on the stored assessment state for later
 * guidance and can never reach a judgment, so they can never move a v2 score.</p>
 */
public record ALevelJudgmentInput(List<String> subjects) {

    public ALevelJudgmentInput {
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects must not be null"));
        if (subjects.size() < ALevelAssessmentState.MINIMUM_SUBJECTS
                || subjects.size() > ALevelAssessmentState.MAXIMUM_SUBJECTS) {
            throw new IllegalArgumentException("subjects must contain between " + ALevelAssessmentState.MINIMUM_SUBJECTS
                    + " and " + ALevelAssessmentState.MAXIMUM_SUBJECTS + " subjects");
        }
    }

    /** The subject combination of {@code state}, already canonicalised and de-duplicated by the state itself. */
    public static ALevelJudgmentInput of(ALevelAssessmentState state) {
        return new ALevelJudgmentInput(Objects.requireNonNull(state, "state must not be null").selectedSubjects());
    }
}
