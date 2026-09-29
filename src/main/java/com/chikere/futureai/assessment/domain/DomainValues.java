package com.chikere.futureai.assessment.domain;

import java.util.List;
import java.util.Objects;

final class DomainValues {

    private DomainValues() {
    }

    static String requiredText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    static List<String> textList(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        return values.stream()
                .map(value -> requiredText(value, fieldName + " entry"))
                .toList();
    }

    static List<String> nonEmptyTextList(List<String> values, String fieldName) {
        List<String> copy = textList(values, fieldName);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return copy;
    }
}
