package com.chikere.futureai.narrative.alevel.paid;

import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code a-level-paid-narrative-v1} prose: five short explanations of the deterministic paid report. It decides
 * nothing. The subject profile, guidance, skill priorities, actions and safeguards come from frozen Java policy and
 * appear unchanged whether or not this narrative is available. Fields may be null when a provider returns malformed
 * output; the validator rejects that.
 */
public record ALevelPaidNarrative(
        String profileExplanation,
        String capabilityExplanation,
        String optionsExplanation,
        String aiLearningExplanation,
        String actionPlanIntroduction) {

    public static final int MAXIMUM_FIELD_LENGTH = 900;

    /** The fields in report order. */
    public List<String> fields() {
        return Stream.of(profileExplanation, capabilityExplanation, optionsExplanation, aiLearningExplanation,
                actionPlanIntroduction).map(value -> value == null ? "" : value).toList();
    }
}
