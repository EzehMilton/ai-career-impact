package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalNarrativeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalNarrativeService.class);

    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalNarrativeStore narrativeStore;
    private final ProfessionalNarrativeGenerator generator;
    private final ProfessionalNarrativeContextFactory contextFactory;
    private final ProfessionalNarrativeValidator validator;
    private final Clock clock;

    public ProfessionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator,
            ProfessionalNarrativeContextFactory contextFactory,
            ProfessionalNarrativeValidator validator,
            Clock clock) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.generator = Objects.requireNonNull(generator);
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
    }

    public StoredProfessionalNarrative generateFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Optional<StoredProfessionalNarrative> existing = narrativeStore.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        Instant attemptedAt = Instant.now(clock);
        var pending = stored(assessmentId, NarrativeStatus.PENDING, Optional.empty(),
                attemptedAt, Optional.empty());
        narrativeStore.save(pending);
        try {
            ProfessionalNarrativeContext context = contextFactory.create(assessment);
            ProfessionalNarrative narrative = validator.validate(generator.generate(context));
            var available = stored(assessmentId, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                    attemptedAt, Optional.of(Instant.now(clock)));
            narrativeStore.save(available);
            LOGGER.info("Narrative journey=professional assessmentId={} outcome=AVAILABLE", assessmentId);
            return available;
        }
        catch (RuntimeException generationFailure) {
            var failed = stored(assessmentId, NarrativeStatus.FAILED, Optional.empty(),
                    attemptedAt, Optional.empty());
            narrativeStore.save(failed);
            LOGGER.warn("Narrative journey=professional assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, generationFailure.getClass().getSimpleName());
            return failed;
        }
    }

    public Optional<StoredProfessionalNarrative> findFor(UUID assessmentId) {
        return narrativeStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private StoredProfessionalNarrative stored(
            UUID assessmentId,
            NarrativeStatus status,
            Optional<ProfessionalNarrative> narrative,
            Instant attemptedAt,
            Optional<Instant> generatedAt) {
        return new StoredProfessionalNarrative(assessmentId, status, narrative, generator.source(),
                generator.providerIdentifier(), generator.modelIdentifier(),
                ProfessionalNarrativeVersions.PROMPT_AND_SCHEMA, attemptedAt, generatedAt);
    }
}
