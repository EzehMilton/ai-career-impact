package com.chikere.futureai.responsibility.professional.typesafe;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The Jev state for responsibility analysis: the role context plus the selected responsibilities under their
 * stable keys. Responsibility text appears only here, as data.
 */
public final class ProfessionalResponsibilityStateMapper {

    public Map<String, Object> map(ProfessionalResponsibilityAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("journey", "PROFESSIONAL_RESPONSIBILITIES");
        state.put("profession", request.profession());
        state.put("work_description", request.workDescription());
        state.put("skills", request.skills());
        state.put("responsibilities", request.keyedResponsibilities());
        return Collections.unmodifiableMap(state);
    }
}
