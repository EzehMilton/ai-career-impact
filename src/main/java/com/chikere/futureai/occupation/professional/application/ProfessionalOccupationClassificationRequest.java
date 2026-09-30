package com.chikere.futureai.occupation.professional.application;

import java.util.List;
import java.util.Objects;

public record ProfessionalOccupationClassificationRequest(
        String profession,
        List<String> responsibilities,
        List<String> skills,
        String geography) {

    public ProfessionalOccupationClassificationRequest {
        profession = required(profession, "profession");
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities));
        skills = List.copyOf(Objects.requireNonNull(skills));
        geography = required(geography, "geography");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
