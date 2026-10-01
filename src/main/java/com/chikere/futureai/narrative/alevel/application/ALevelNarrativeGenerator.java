package com.chikere.futureai.narrative.alevel.application;

import java.util.Optional;

/** Generates presentation text from an A-level assessment whose decision is already final. */
public interface ALevelNarrativeGenerator {

    ALevelNarrative generate(ALevelNarrativeContext context);

    NarrativeSource source();

    String providerIdentifier();

    default Optional<String> modelIdentifier() {
        return Optional.empty();
    }
}
