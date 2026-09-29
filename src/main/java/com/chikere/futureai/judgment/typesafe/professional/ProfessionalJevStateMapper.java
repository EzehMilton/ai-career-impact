package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProfessionalJevStateMapper {

    public Map<String, Object> map(ProfessionalAssessmentState assessmentState) {
        Objects.requireNonNull(assessmentState, "assessmentState must not be null");

        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("journey", "PROFESSIONAL");
        state.put("profession", assessmentState.profession());
        state.put("work_description", assessmentState.workDescription());
        state.put("responsibilities", assessmentState.responsibilities());
        state.put("skills", assessmentState.skills());
        assessmentState.yearsOfExperience()
                .ifPresent(years -> state.put("years_experience", years));
        return Collections.unmodifiableMap(state);
    }
}
