package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Trims HTML input and creates the domain value. HTML concerns stop here. */
@Component
public final class UniversityAssessmentFormMapper {

    private final AssessmentTrace trace;

    public UniversityAssessmentFormMapper() {
        this(AssessmentTrace.disabled());
    }

    @Autowired
    public UniversityAssessmentFormMapper(ObjectProvider<AssessmentTrace> trace) {
        this(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    UniversityAssessmentFormMapper(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityAssessmentState toState(UniversityAssessmentForm form) {
        Objects.requireNonNull(form, "form must not be null");
        return trace.formMapping(JourneyType.UNIVERSITY, () -> submitted(form), () -> map(form), state -> state);
    }

    /** The form exactly as submitted, before trimming, splitting and validation-driven mapping. */
    private Map<String, Object> submitted(UniversityAssessmentForm form) {
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("courseTitle", form.getCourseTitle());
        submitted.put("courseDescription", form.getCourseDescription());
        submitted.put("keyModules", form.getKeyModules());
        submitted.put("skillsDeveloped", form.getSkillsDeveloped());
        submitted.put("preferredCareerAreas", form.getPreferredCareerAreas());
        submitted.put("institution", form.getInstitution());
        submitted.put("country", form.getCountry());
        return submitted;
    }

    private UniversityAssessmentState map(UniversityAssessmentForm form) {
        return new UniversityAssessmentState(
                form.getCourseTitle().strip(),
                form.getCourseDescription().strip(),
                lines(form.getKeyModules()),
                lines(form.getSkillsDeveloped()),
                lines(form.getPreferredCareerAreas()),
                optional(form.getInstitution()),
                optional(form.getCountry()));
    }

    private List<String> lines(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private Optional<String> optional(String value) {
        return Optional.ofNullable(value).map(String::strip).filter(text -> !text.isEmpty());
    }
}
