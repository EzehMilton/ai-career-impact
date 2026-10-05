package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrative is downstream of the committed assessment. It never reassesses, and a generation or
 * persistence failure can never invalidate the saved deterministic result.
 */
public final class ALevelNarrativeService implements ALevelNarrativeGeneration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelNarrativeService.class);

    private final ALevelAssessmentStore assessmentStore;
    private final ALevelNarrativeStore narrativeStore;
    private final ALevelNarrativeGenerator generator;
    private final ALevelNarrativeContextFactory contextFactory;
    private final ALevelNarrativeValidator validator;
    private final Clock clock;

    public ALevelNarrativeService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator,
            ALevelNarrativeContextFactory contextFactory, ALevelNarrativeValidator validator, Clock clock) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.generator = Objects.requireNonNull(generator);
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public StoredALevelNarrative generateFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Optional<StoredALevelNarrative> existing = narrativeStore.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            // Historical narratives, including older prompt versions and failed attempts, are never replaced.
            return existing.orElseThrow();
        }
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        Instant attemptedAt = Instant.now(clock);
        narrativeStore.save(stored(assessmentId, NarrativeStatus.PENDING, Optional.empty(),
                attemptedAt, Optional.empty()));
        try {
            ALevelNarrativeContext context = contextFactory.create(assessment);
            ALevelNarrative narrative = validator.validate(generator.generate(context), context);
            var available = stored(assessmentId, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                    attemptedAt, Optional.of(Instant.now(clock)));
            narrativeStore.save(available);
            LOGGER.info("Narrative journey=alevel assessmentId={} outcome=AVAILABLE", assessmentId);
            return available;
        }
        catch (RuntimeException generationFailure) {
            var failed = stored(assessmentId, NarrativeStatus.FAILED, Optional.empty(),
                    attemptedAt, Optional.empty());
            narrativeStore.save(failed);
            LOGGER.warn("Narrative journey=alevel assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, generationFailure.getClass().getSimpleName());
            return failed;
        }
    }

    public Optional<StoredALevelNarrative> findFor(UUID assessmentId) {
        return narrativeStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private StoredALevelNarrative stored(UUID assessmentId, NarrativeStatus status,
            Optional<ALevelNarrative> narrative, Instant attemptedAt, Optional<Instant> generatedAt) {
        return new StoredALevelNarrative(assessmentId, status, narrative, generator.source(),
                generator.providerIdentifier(), generator.modelIdentifier(),
                ALevelNarrativeVersions.PROMPT_AND_SCHEMA, attemptedAt, generatedAt);
    }
}
