package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public ALevelNarrativeService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator,
            ALevelNarrativeContextFactory contextFactory, ALevelNarrativeValidator validator, Clock clock) {
        this(assessmentStore, narrativeStore, generator, contextFactory, validator, clock, JourneyMetrics.disabled());
    }

    public ALevelNarrativeService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator,
            ALevelNarrativeContextFactory contextFactory, ALevelNarrativeValidator validator, Clock clock,
            JourneyMetrics metrics) {
        this(assessmentStore, narrativeStore, generator, contextFactory, validator, clock, metrics,
                AssessmentTrace.disabled());
    }

    public ALevelNarrativeService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator,
            ALevelNarrativeContextFactory contextFactory, ALevelNarrativeValidator validator, Clock clock,
            JourneyMetrics metrics, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.generator = Objects.requireNonNull(generator);
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Only a historical v1 assessment has a free narrative. A v2 profile has a deterministic free result and will
     * have its own paid narrative, so for it nothing is called or stored and the result is empty.
     */
    @Override
    public Optional<StoredALevelNarrative> generateIfApplicable(UUID assessmentId) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        if (assessment.result().usesSubjectProfile()) {
            return Optional.empty();
        }
        return Optional.of(generateFor(assessmentId));
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
        if (assessment.result().usesSubjectProfile()) {
            throw new IllegalStateException("a v2 profile has no free narrative; use generateIfApplicable");
        }
        narrativeStore.save(stored(assessmentId, NarrativeStatus.PENDING, Optional.empty(),
                attemptedAt, Optional.empty()));
        try {
            ALevelNarrativeContext context = contextFactory.create(assessment);
            trace.event(JourneyType.A_LEVEL, TraceStage.NARRATIVE_REQUEST, () -> narrativeRequest(context));
            ALevelNarrative generated = metrics.timed("narrative", "a-level", () -> trace.timed(JourneyType.A_LEVEL,
                    TraceStage.NARRATIVE_RESPONSE, "narrativeModelMs", () -> generator.generate(context),
                    value -> value));
            ALevelNarrative narrative = validator.validate(generated, context);
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

    private Map<String, Object> narrativeRequest(ALevelNarrativeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", "ASSESSMENT_NARRATIVE");
        payload.put("purposeNote", "The only narrative AI call for an assessment. Its fields feed both the free "
                + "interpretation and the detailed report; the detailed report makes no further AI call.");
        payload.put("provider", generator.source());
        payload.put("providerIdentifier", generator.providerIdentifier());
        payload.put("model", generator.modelIdentifier().orElse(null));
        payload.put("promptAndSchemaVersion", ALevelNarrativeVersions.PROMPT_AND_SCHEMA);
        payload.put("outputType", ALevelNarrative.class.getSimpleName());
        payload.put("context", context);
        payload.put("note", "For the OpenAI provider the exact system and user messages and the output schema "
                + "follow as NARRATIVE_PROVIDER_REQUEST.");
        return payload;
    }
}
