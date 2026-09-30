package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

@Component
public final class ProfessionalAssessmentFormMapper {

    public ProfessionalAssessmentState toState(ProfessionalAssessmentForm form) {
        Objects.requireNonNull(form, "form must not be null");
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
