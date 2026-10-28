package com.chikere.futureai.narrative.university.paid;

import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code university-paid-narrative-v1} prose: five short perspectives that explain the deterministic paid report.
 * It decides nothing; the report's scores, bands, module classifications, skills, safeguards and actions come from
 * frozen Java policy and appear unchanged whether or not this narrative is available. Fields may be null when a
 * provider returns malformed output; the validator rejects that.
 */
public record UniversityPaidNarrative(
        String overallPerspective,
        String aiLearningPerspective,
        String humanPracticalPerspective,
        String transferablePerspective,
        String actionPlanIntroduction) {

    public static final int MAXIMUM_FIELD_LENGTH = 900;

    /** The fields in report order. */
    public List<String> fields() {
        return Stream.of(overallPerspective, aiLearningPerspective, humanPracticalPerspective, transferablePerspective,
                actionPlanIntroduction).map(value -> value == null ? "" : value).toList();
    }
}
