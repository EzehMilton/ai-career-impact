package com.chikere.futureai.cv.professional.application;

import java.util.Objects;
import java.util.OptionalInt;

/** Transient document text. Callers must not persist or log {@link #text()}. */
public record ProfessionalCvText(String text, OptionalInt pageCount, String parserVersion) {
    public ProfessionalCvText {
        text = Objects.requireNonNull(text, "text must not be null");
        pageCount = Objects.requireNonNull(pageCount, "pageCount must not be null");
        if (parserVersion == null || parserVersion.isBlank()) {
            throw new IllegalArgumentException("parserVersion must not be blank");
        }
    }
}
