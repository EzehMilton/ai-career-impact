package com.chikere.futureai.narrative.university.application;

import java.util.Optional;

/** Generates presentation text from a university assessment whose decision is already final. */
public interface UniversityNarrativeGenerator {

    UniversityNarrative generate(UniversityNarrativeContext context);

    NarrativeSource source();

    String providerIdentifier();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
