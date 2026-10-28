package com.chikere.futureai.narrative.alevel.paid;

import java.util.Objects;
import java.util.Optional;

/** Explains the frozen A-level paid-report findings. Never decides, ranks or recommends anything itself. */
public interface ALevelPaidNarrativeGenerator {

    GeneratedNarrative generate(ALevelPaidNarrativeContext context);

    /** The single repair attempt: the same context plus the failed rule and the draft text that failed. */
    GeneratedNarrative repair(ALevelPaidNarrativeContext context, RepairFeedback feedback);

    String providerIdentifier();

    Optional<String> modelIdentifier();

    /** A provider response: the structured narrative (possibly malformed) and the provider's request id if known. */
    record GeneratedNarrative(ALevelPaidNarrative narrative, Optional<String> providerRequestId,
            Optional<String> model) {
        public GeneratedNarrative {
            Objects.requireNonNull(providerRequestId);
            Objects.requireNonNull(model);
        }
    }

    /** Why the first draft was rejected, for the one repair. */
    record RepairFeedback(String rule, String reason, String offendingText) {
    }
}
