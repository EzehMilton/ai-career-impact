package com.chikere.futureai.evidence.university.application;

import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The narrow request an evidence provider receives. It carries no entity, credential or SDK type. */
public record UniversityEvidenceRequest(
        UUID assessmentId,
        String courseTitle,
        List<String> keyModules,
        List<String> skillsDeveloped,
        List<String> preferredCareerAreas,
        Optional<String> institution,
        String geography,
        JourneyType journeyType) {

    public UniversityEvidenceRequest {
        Objects.requireNonNull(assessmentId);
        courseTitle = required(courseTitle, "courseTitle");
        keyModules = List.copyOf(Objects.requireNonNull(keyModules));
        skillsDeveloped = List.copyOf(Objects.requireNonNull(skillsDeveloped));
        preferredCareerAreas = List.copyOf(Objects.requireNonNull(preferredCareerAreas));
        institution = Objects.requireNonNull(institution).map(String::strip).filter(value -> !value.isEmpty());
        geography = required(geography, "geography");
        Objects.requireNonNull(journeyType);
        if (journeyType != JourneyType.UNIVERSITY) {
            throw new IllegalArgumentException("journeyType must be UNIVERSITY");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
