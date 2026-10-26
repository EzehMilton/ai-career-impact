package com.chikere.futureai.narrative.professional.application;

import java.util.Objects;

/**
 * Application-generated feedback for the single repair attempt: the rule a draft failed, the application's own
 * reason, and the draft sentence that failed. The sentence is model output and is only ever quoted as data.
 */
public record NarrativeRepairFeedback(String rule, String reason, String offendingSentence) {

    public NarrativeRepairFeedback {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
