package com.chikere.futureai.report.university.application;

import java.util.List;
import java.util.Objects;

/**
 * The customer-facing {@code university-decision-v2} course profile, composed deterministically from the stored
 * three measures. There is no overall score and no "strongest measure" headline.
 *
 * @param moreInformationRequired the course information was too thin for measures; {@code measures} is then empty
 */
public record UniversityCourseProfile(
        String heading,
        boolean moreInformationRequired,
        List<Measure> measures,
        String summary) {

    public UniversityCourseProfile {
        Objects.requireNonNull(heading);
        measures = List.copyOf(measures);
        Objects.requireNonNull(summary);
    }

    /**
     * One measure as shown: the boundary-safe score, its band in context, a plain-English explanation, up to two
     * drivers and limiting factors, and a lower-confidence note when it applies.
     *
     * @param confidenceNote empty when the measure needs no warning
     */
    public record Measure(
            String id,
            String name,
            String score,
            String band,
            String bandLabel,
            String explanation,
            List<String> strengthenedBy,
            List<String> heldBackBy,
            String confidenceNote) {

        public Measure {
            strengthenedBy = List.copyOf(strengthenedBy);
            heldBackBy = List.copyOf(heldBackBy);
        }
    }
}
