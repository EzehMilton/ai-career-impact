package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import com.chikere.futureai.trace.TraceStage;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalNarrativeService implements ProfessionalNarrativeGeneration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalNarrativeService.class);

    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalNarrativeStore narrativeStore;
    private final ProfessionalNarrativeGenerator generator;
    private final ProfessionalNarrativeContextFactory contextFactory;
    private final ProfessionalNarrativeValidator validator;
    private final Clock clock;
    private final JourneyMetrics metrics;
    /** Only content rules are repaired; a missing narrative is a provider failure, not prose to correct. */
    private static final java.util.function.Predicate<String> REPAIRABLE = rule -> !rule.equals("NARRATIVE_PRESENT");
    private final AssessmentTrace trace;

    public ProfessionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator,
            ProfessionalNarrativeContextFactory contextFactory,
            ProfessionalNarrativeValidator validator,
            Clock clock) {
        this(assessmentStore, narrativeStore, generator, contextFactory, validator, clock, JourneyMetrics.disabled());
    }

    public ProfessionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator,
            ProfessionalNarrativeContextFactory contextFactory,
            ProfessionalNarrativeValidator validator,
            Clock clock,
            JourneyMetrics metrics) {
        this(assessmentStore, narrativeStore, generator, contextFactory, validator, clock, metrics,
                AssessmentTrace.disabled());
    }

    public ProfessionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator,
            ProfessionalNarrativeContextFactory contextFactory,
            ProfessionalNarrativeValidator validator,
            Clock clock,
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

    @Override
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
        var attempts = new Attempts();
        try {
            ProfessionalNarrativeContext context = contextFactory.create(assessment);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.NARRATIVE_REQUEST, () -> narrativeRequest(context));
            ProfessionalNarrative generated = metrics.timed("narrative", "professional",
                    () -> trace.timed(JourneyType.PROFESSIONAL,
                    TraceStage.NARRATIVE_RESPONSE, "narrativeModelMs", () -> generator.generate(context),
                    value -> attempt(1, value)));
            ProfessionalNarrative narrative = validateWithOneRepair(context, generated, attempts);
            var available = stored(assessmentId, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                    attemptedAt, Optional.of(Instant.now(clock)));
            narrativeStore.save(available);
            LOGGER.info("Narrative journey=professional assessmentId={} outcome=AVAILABLE attempts={}",
                    assessmentId, attempts.count);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.NARRATIVE_OUTCOME,
                    () -> outcome("AVAILABLE", null, attempts));
            return available;
        }
        catch (RuntimeException generationFailure) {
            var failed = stored(assessmentId, NarrativeStatus.FAILED, Optional.empty(),
                    attemptedAt, Optional.empty());
            narrativeStore.save(failed);
            LOGGER.warn("Narrative journey=professional assessmentId={} outcome=FAILED failureType={} attempts={}",
                    assessmentId, generationFailure.getClass().getSimpleName(), attempts.count);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.NARRATIVE_OUTCOME,
                    () -> outcome("FAILED", generationFailure, attempts));
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

    /**
     * Validates a draft and, if it breaks a content rule, makes exactly one repair attempt with the failed rule and
     * sentence. The rejected draft is never stored or shown. A missing narrative, a provider error or any other
     * failure is not repaired: it propagates and the narrative is stored as failed, as before.
     */
    private ProfessionalNarrative validateWithOneRepair(ProfessionalNarrativeContext context,
            ProfessionalNarrative generated, Attempts attempts) {
        attempts.count = 1;
        try {
            return validator.validate(generated, context);
        }
        catch (NarrativeRuleViolation violation) {
            if (!REPAIRABLE.test(violation.rule())) {
                throw violation;
            }
            attempts.firstFailedRule = violation.rule();
            var feedback = new NarrativeRepairFeedback(violation.rule(), violation.reason(), violation.triggeringText());
            trace.event(JourneyType.PROFESSIONAL, TraceStage.NARRATIVE_REPAIR_REQUEST, () -> repairRequest(feedback));
            attempts.count = 2;
            ProfessionalNarrative repaired = metrics.timed("narrative", "professional",
                    () -> trace.timed(JourneyType.PROFESSIONAL, TraceStage.NARRATIVE_RESPONSE,
                            "narrativeRepairModelMs", () -> generator.repair(context, feedback),
                            value -> attempt(2, value)));
            return validator.validate(repaired, context);
        }
    }

    /** Provider calls made for one generateFor call, kept local so concurrent requests never share it. */
    private static final class Attempts {
        private int count;
        private String firstFailedRule;
    }

    private static Map<String, Object> attempt(int attempt, ProfessionalNarrative narrative) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", attempt);
        payload.put("narrative", narrative);
        return payload;
    }

    private Map<String, Object> repairRequest(NarrativeRepairFeedback feedback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", 2);
        payload.put("reason", "the first draft failed deterministic validation; one repair attempt is made");
        payload.put("failedRule", feedback.rule());
        payload.put("ruleReason", feedback.reason());
        payload.put("offendingSentence", feedback.offendingSentence());
        payload.put("rejectedDraftStored", false);
        return payload;
    }

    private static Map<String, Object> outcome(String status, RuntimeException failure, Attempts attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("attempts", attempts.count);
        payload.put("repairAttempted", attempts.count > 1);
        payload.put("firstFailedRule", attempts.firstFailedRule);
        if (failure instanceof NarrativeRuleViolation violation) {
            payload.put("finalFailedRule", violation.rule());
        }
        else if (failure != null) {
            payload.put("failureType", failure.getClass().getName());
        }
        return payload;
    }

    private Map<String, Object> narrativeRequest(ProfessionalNarrativeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", "ASSESSMENT_NARRATIVE");
        payload.put("purposeNote", "The only narrative AI call for an assessment. Its fields feed both the free "
                + "interpretation and the detailed report; the detailed report makes no further AI call.");
        payload.put("provider", generator.source());
        payload.put("providerIdentifier", generator.providerIdentifier());
        payload.put("model", generator.modelIdentifier().orElse(null));
        payload.put("promptAndSchemaVersion", ProfessionalNarrativeVersions.PROMPT_AND_SCHEMA);
        payload.put("outputType", ProfessionalNarrative.class.getSimpleName());
        payload.put("context", context);
        payload.put("note", "For the OpenAI provider the exact system and user messages and the output schema "
                + "follow as NARRATIVE_PROVIDER_REQUEST.");
        return payload;
    }
}
