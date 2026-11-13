package com.chikere.futureai.judgment.professional.schema;

import java.util.Objects;

public record ProfessionalScoreLevel(int value, String description) {

    public ProfessionalScoreLevel {
        if (value < 0) throw new IllegalArgumentException("value must not be negative");
        Objects.requireNonNull(description, "description must not be null");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        description = description.strip();
    }
}
