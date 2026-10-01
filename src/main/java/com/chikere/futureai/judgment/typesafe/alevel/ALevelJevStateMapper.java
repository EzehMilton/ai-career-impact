package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Maps the A-level domain state into the vendor-neutral state map Jev receives. */
public final class ALevelJevStateMapper {

    public Map<String, Object> map(ALevelAssessmentState assessmentState) {
        Objects.requireNonNull(assessmentState, "assessmentState must not be null");

        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("journey", "A_LEVEL");
        state.put("selected_subjects", assessmentState.selectedSubjects());
        if (!assessmentState.predictedGrades().isEmpty()) {
            state.put("predicted_grades", assessmentState.predictedGrades());
        }
        state.put("strengths_and_interests", assessmentState.strengthsAndInterests());
        state.put("preferred_degree_areas", assessmentState.preferredDegreeAreas());
        state.put("preferred_career_areas", assessmentState.preferredCareerAreas());
        assessmentState.country().ifPresent(value -> state.put("country", value));
        assessmentState.additionalContext().ifPresent(value -> state.put("additional_context", value));
        return Collections.unmodifiableMap(state);
    }
}
