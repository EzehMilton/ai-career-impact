package com.chikere.futureai.occupation.professional.application;

import java.util.Objects;

public record ProfessionalOccupationClassification(
        String canonicalOccupation,
        OccupationClassificationSystem classificationSystem,
        String classificationCode,
        OccupationClassificationSource source,
        OccupationClassificationQuality quality,
        String classifierIdentifier,
        String classifierVersion) {

    public ProfessionalOccupationClassification {
        canonicalOccupation = required(canonicalOccupation, "canonicalOccupation");
        Objects.requireNonNull(classificationSystem);
        classificationCode = required(classificationCode, "classificationCode");
        Objects.requireNonNull(source);
        Objects.requireNonNull(quality);
        classifierIdentifier = required(classifierIdentifier, "classifierIdentifier");
        classifierVersion = required(classifierVersion, "classifierVersion");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
