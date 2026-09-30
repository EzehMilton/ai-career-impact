package com.chikere.futureai.narrative.professional.application;

import java.util.Optional;

/** Generates presentation text from an assessment whose decision is already final. */
public interface ProfessionalNarrativeGenerator {

    ProfessionalNarrative generate(ProfessionalNarrativeContext context);

    NarrativeSource source();

    String providerIdentifier();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
