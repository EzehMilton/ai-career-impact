package com.chikere.futureai.judgment.typesafe.university;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Maps the university domain state into the vendor-neutral state map Jev receives. */
public final class UniversityJevStateMapper {

    public Map<String, Object> map(UniversityAssessmentState assessmentState) {
        Objects.requireNonNull(assessmentState, "assessmentState must not be null");

        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("journey", "UNIVERSITY");
        state.put("course_title", assessmentState.courseTitle());
        state.put("course_description", assessmentState.courseDescription());
        state.put("key_modules", assessmentState.keyModules());
        state.put("skills_developed", assessmentState.skillsDeveloped());
        state.put("preferred_career_areas", assessmentState.preferredCareerAreas());
        assessmentState.institution().ifPresent(value -> state.put("institution", value));
        assessmentState.country().ifPresent(value -> state.put("country", value));
        return Collections.unmodifiableMap(state);
    }
}
