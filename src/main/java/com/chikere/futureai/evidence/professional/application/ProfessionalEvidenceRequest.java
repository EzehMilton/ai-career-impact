package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProfessionalEvidenceRequest(
        UUID assessmentId,
        String profession,
        List<String> responsibilities,
        List<String> skills,
        String geography,
        JourneyType journeyType,
        Optional<String> occupationClassification) {

    public ProfessionalEvidenceRequest {
        Objects.requireNonNull(assessmentId);
        profession = required(profession, "profession");
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities));
        skills = List.copyOf(Objects.requireNonNull(skills));
        geography = required(geography, "geography");
        Objects.requireNonNull(journeyType);
        occupationClassification = Objects.requireNonNull(occupationClassification)
                .map(String::strip).filter(value -> !value.isEmpty());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
