package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;

import java.util.Objects;
import java.util.Optional;

/** Explains the frozen paid-report findings. Never decides, ranks or recommends anything itself. */
public interface ProfessionalPaidNarrativeGenerator {

    GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context);

    /** The single repair attempt: the same context plus the failed rule and the draft text that failed. */
    GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback);

    String providerIdentifier();

    Optional<String> modelIdentifier();

    /** A provider response: the structured narrative (possibly malformed) and the provider's request id if known. */
    record GeneratedPaidNarrative(ProfessionalPaidNarrative narrative, Optional<String> providerRequestId,
            Optional<String> model) {
        public GeneratedPaidNarrative {
            Objects.requireNonNull(providerRequestId);
            Objects.requireNonNull(model);
        }
    }
}
