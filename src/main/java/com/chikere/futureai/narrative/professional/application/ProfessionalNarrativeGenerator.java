package com.chikere.futureai.narrative.professional.application;

import java.util.Optional;

/** Generates presentation text from an assessment whose decision is already final. */
public interface ProfessionalNarrativeGenerator {

    ProfessionalNarrative generate(ProfessionalNarrativeContext context);

    /**
     * The single repair attempt after a draft failed validation: the same authoritative context plus the failed
     * rule and sentence. A deterministic generator has nothing to correct, so by default it simply generates again.
     */
    default ProfessionalNarrative repair(ProfessionalNarrativeContext context, NarrativeRepairFeedback feedback) {
        return generate(context);
    }

    NarrativeSource source();

    String providerIdentifier();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
