package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
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
import java.util.OptionalInt;

@Component
public final class ProfessionalAssessmentFormMapper {

    private final AssessmentTrace trace;

    public ProfessionalAssessmentFormMapper() {
        this(AssessmentTrace.disabled());
    }

    @Autowired
    public ProfessionalAssessmentFormMapper(ObjectProvider<AssessmentTrace> trace) {
        this(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    ProfessionalAssessmentFormMapper(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ProfessionalAssessmentState toState(ProfessionalAssessmentForm form) {
        Objects.requireNonNull(form, "form must not be null");
        return trace.formMapping(JourneyType.PROFESSIONAL, () -> submitted(form), () -> map(form), state -> state);
    }

    /** The form exactly as submitted, before trimming, splitting and validation-driven mapping. */
    private Map<String, Object> submitted(ProfessionalAssessmentForm form) {
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("profession", form.getProfession());
        submitted.put("workDescription", form.getWorkDescription());
        submitted.put("responsibilities", form.getResponsibilities());
        submitted.put("skills", form.getSkills());
        submitted.put("yearsOfExperience", form.getYearsOfExperience());
        return submitted;
    }

    private ProfessionalAssessmentState map(ProfessionalAssessmentForm form) {
        return new ProfessionalAssessmentState(
                form.getProfession().strip(),
                form.getWorkDescription().strip(),
                lines(form.getResponsibilities()),
                lines(form.getSkills()),
                form.getYearsOfExperience() == null
                        ? OptionalInt.empty()
                        : OptionalInt.of(form.getYearsOfExperience()));
    }

    private List<String> lines(String value) {
        return Arrays.stream(value.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
