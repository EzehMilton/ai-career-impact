package com.chikere.futureai.judgment.professional.schema;

import java.util.Objects;

public record ProfessionalPredicateQuestion(
        String id,
        String instructions,
        String whenTrue,
        String whenFalse) implements ProfessionalQuestion {

    public ProfessionalPredicateQuestion {
        id = required(id, "id");
        instructions = required(instructions, "instructions");
        whenTrue = required(whenTrue, "whenTrue");
        whenFalse = required(whenFalse, "whenFalse");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
