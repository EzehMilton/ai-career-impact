package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.Map;
import java.util.Objects;

/**
 * Stage A1/A2 design: what an {@code a-level-v2} Jev request may contain. Test-side until approved; it replaces
 * {@code ALevelJevStateMapper} only for new v2 assessments.
 *
 * <p><strong>Scoring input</strong> is the canonical subject list and nothing else. Everything else on the state is
 * <strong>guidance-only</strong>: predicted grades, strengths and interests, preferred degree and career areas, country
 * and free-text context describe the student, not the combination, so they never reach a judgment and can never move
 * a v2 score. They stay on the stored state for the later paid guidance.</p>
 */
final class ALevelV2JudgmentInput {

    static final String SELECTED_SUBJECTS = "selected_subjects";

    private ALevelV2JudgmentInput() {
    }

    static Map<String, Object> map(ALevelAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        return Map.of(SELECTED_SUBJECTS, state.selectedSubjects());
    }
}
