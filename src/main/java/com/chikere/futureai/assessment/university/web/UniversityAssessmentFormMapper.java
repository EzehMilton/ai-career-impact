package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Trims HTML input and creates the domain value. HTML concerns stop here. */
@Component
public final class UniversityAssessmentFormMapper {

    public UniversityAssessmentState toState(UniversityAssessmentForm form) {
        Objects.requireNonNull(form, "form must not be null");
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
