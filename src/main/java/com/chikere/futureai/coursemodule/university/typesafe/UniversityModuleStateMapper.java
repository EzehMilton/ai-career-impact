package com.chikere.futureai.coursemodule.university.typesafe;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The Jev state for module analysis: the course context plus the selected modules under their stable keys. Module
 * titles appear only here, as data. Preferred career areas are never included.
 */
public final class UniversityModuleStateMapper {

    public Map<String, Object> map(UniversityModuleAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("journey", "UNIVERSITY_MODULES");
        state.put("course_title", request.courseTitle());
        state.put("course_description", request.courseDescription());
        state.put("skills_developed", request.skillsDeveloped());
        state.put("modules", request.keyedModules());
        return Collections.unmodifiableMap(state);
    }
}
