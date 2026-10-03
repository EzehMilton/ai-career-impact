package com.chikere.futureai.narrative.university.application;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
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
public final class UniversityNarrativeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityNarrativeService.class);

    private final UniversityAssessmentStore assessmentStore;
    private final UniversityNarrativeStore narrativeStore;
    private final UniversityNarrativeGenerator generator;
    private final UniversityNarrativeContextFactory contextFactory;
    private final UniversityNarrativeValidator validator;
    private final Clock clock;

    public UniversityNarrativeService(
            UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore,
            UniversityNarrativeGenerator generator,
            UniversityNarrativeContextFactory contextFactory,
            UniversityNarrativeValidator validator,
            Clock clock) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.generator = Objects.requireNonNull(generator);
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
    }

    public StoredUniversityNarrative generateFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Optional<StoredUniversityNarrative> existing = narrativeStore.findByAssessmentId(assessmentId);
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
            UniversityNarrativeContext context = contextFactory.create(assessment);
            UniversityNarrative narrative = validator.validate(generator.generate(context));
            var available = stored(assessmentId, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                    attemptedAt, Optional.of(Instant.now(clock)));
            narrativeStore.save(available);
            LOGGER.info("Narrative journey=university assessmentId={} outcome=AVAILABLE", assessmentId);
            return available;
        }
        catch (RuntimeException generationFailure) {
            var failed = stored(assessmentId, NarrativeStatus.FAILED, Optional.empty(),
                    attemptedAt, Optional.empty());
            narrativeStore.save(failed);
            LOGGER.warn("Narrative journey=university assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, generationFailure.getClass().getSimpleName());
            return failed;
        }
    }

    public Optional<StoredUniversityNarrative> findFor(UUID assessmentId) {
        return narrativeStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private StoredUniversityNarrative stored(UUID assessmentId, NarrativeStatus status,
            Optional<UniversityNarrative> narrative, Instant attemptedAt, Optional<Instant> generatedAt) {
        return new StoredUniversityNarrative(assessmentId, status, narrative, generator.source(),
                generator.providerIdentifier(), generator.modelIdentifier(),
                UniversityNarrativeVersions.PROMPT_AND_SCHEMA, attemptedAt, generatedAt);
    }
}
