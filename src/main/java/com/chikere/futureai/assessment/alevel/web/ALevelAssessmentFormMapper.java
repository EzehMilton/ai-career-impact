package com.chikere.futureai.assessment.alevel.web;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.ALevelSubjects;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Trims HTML input and creates the domain value. HTML concerns stop here.
 *
 * <p>Subjects may be separated by new lines or commas, because students write them both ways.
 * Degree and career areas are split on new lines only, so a multi-word area such as "Philosophy,
 * Politics and Economics" survives intact.</p>
 *
 * <p>Predicted grades are entered one per line as {@code Subject: Grade}. A line without a colon,
 * or naming a subject that is not in the list, is ignored rather than failing the form: a student
 * should never be blocked from an assessment by grade formatting.</p>
 */
@Component
public final class ALevelAssessmentFormMapper {

    private final AssessmentTrace trace;

    public ALevelAssessmentFormMapper() {
        this(AssessmentTrace.disabled());
    }

    @Autowired
    public ALevelAssessmentFormMapper(ObjectProvider<AssessmentTrace> trace) {
        this(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    ALevelAssessmentFormMapper(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ALevelAssessmentState toState(ALevelAssessmentForm form) {
        Objects.requireNonNull(form, "form must not be null");
        return trace.formMapping(JourneyType.A_LEVEL, () -> submitted(form), () -> map(form), state -> state);
    }

    /** The form exactly as submitted, before trimming, splitting and validation-driven mapping. */
    private Map<String, Object> submitted(ALevelAssessmentForm form) {
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("selectedSubjects", form.getSelectedSubjects());
        submitted.put("predictedGrades", form.getPredictedGrades());
        submitted.put("strengthsAndInterests", form.getStrengthsAndInterests());
        submitted.put("preferredDegreeAreas", form.getPreferredDegreeAreas());
        submitted.put("preferredCareerAreas", form.getPreferredCareerAreas());
        submitted.put("country", form.getCountry());
        submitted.put("additionalContext", form.getAdditionalContext());
        return submitted;
    }

    private ALevelAssessmentState map(ALevelAssessmentForm form) {
        List<String> canonicalSubjects = subjects(form.getSelectedSubjects());
        return new ALevelAssessmentState(canonicalSubjects,
                grades(form.getPredictedGrades(), canonicalSubjects),
                newLineList(form.getStrengthsAndInterests()),
                newLineList(form.getPreferredDegreeAreas()),
                newLineList(form.getPreferredCareerAreas()),
                optional(form.getCountry()), optional(form.getAdditionalContext()));
    }

    private List<String> subjects(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R|,"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(ALevelSubjects::canonicalise)
                .distinct()
                .toList();
    }

    private Map<String, String> grades(String value, List<String> canonicalSubjects) {
        LinkedHashMap<String, String> grades = new LinkedHashMap<>();
        if (value == null || canonicalSubjects.isEmpty()) {
            return grades;
        }
        for (String line : Arrays.stream(value.split("\\R|,")).map(String::strip)
                .filter(text -> !text.isEmpty()).toList()) {
            int separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }
            String subject = line.substring(0, separator).strip();
            String grade = line.substring(separator + 1).strip();
            if (subject.isEmpty() || grade.isEmpty() || grade.length() > 12) {
                continue;
            }
            String canonical = ALevelSubjects.canonicalise(subject);
            if (canonicalSubjects.contains(canonical)) {
                grades.put(canonical, grade);
            }
        }
        return grades;
    }

    private List<String> newLineList(String value) {
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
