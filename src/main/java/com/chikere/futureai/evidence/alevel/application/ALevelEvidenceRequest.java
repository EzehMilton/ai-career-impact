package com.chikere.futureai.evidence.alevel.application;

import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The narrow request an evidence provider receives. It carries no entity, credential or SDK type. */
public record ALevelEvidenceRequest(
        UUID assessmentId,
        List<String> selectedSubjects,
        List<String> preferredDegreeAreas,
        List<String> preferredCareerAreas,
        String geography,
        JourneyType journeyType) {

    public ALevelEvidenceRequest {
        Objects.requireNonNull(assessmentId);
        selectedSubjects = List.copyOf(Objects.requireNonNull(selectedSubjects));
        preferredDegreeAreas = List.copyOf(Objects.requireNonNull(preferredDegreeAreas));
        preferredCareerAreas = List.copyOf(Objects.requireNonNull(preferredCareerAreas));
        geography = required(geography, "geography");
        Objects.requireNonNull(journeyType);
        if (journeyType != JourneyType.A_LEVEL) {
            throw new IllegalArgumentException("journeyType must be A_LEVEL");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
