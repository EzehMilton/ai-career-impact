package com.chikere.futureai.cv.professional.application;

import java.util.Locale;

/**
 * The one normalisation used to decide whether model output was copied from the CV text: case,
 * whitespace runs, curly quotes and en/em dashes are ignored. There is no fuzzy matching.
 */
public final class ProfessionalCvTextMatching {
    private ProfessionalCvTextMatching() {}

    public static String normalise(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[‘’‚‛′]", "'")
                .replaceAll("[“”„‟″]", "\"")
                .replaceAll("[–—]", "-")
                .replaceAll("[\\s ]+", " ")
                .strip();
    }

    /** Normalises a quoted phrase and drops a leading bullet the model may have copied with it. */
    public static String normaliseQuote(String value) {
        return normalise(value).replaceFirst("^[•▪◦●·*-]+\\s*", "");
    }
}
