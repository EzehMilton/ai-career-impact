package com.chikere.futureai.judgment.professional.schema;

import java.util.List;
import java.util.Objects;

public record ProfessionalScoreQuestion(
        String id,
        String instructions,
        List<ProfessionalScoreLevel> levels) implements ProfessionalQuestion {

    public ProfessionalScoreQuestion {
        id = required(id, "id");
        instructions = required(instructions, "instructions");
        levels = List.copyOf(Objects.requireNonNull(levels, "levels must not be null"));
        if (levels.size() < 2) throw new IllegalArgumentException("levels must contain at least two entries");
        for (int index = 0; index < levels.size(); index++) {
            if (levels.get(index).value() != index) {
                throw new IllegalArgumentException("levels must be contiguous and zero based");
            }
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
