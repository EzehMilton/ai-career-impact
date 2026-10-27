package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator.GeneratedPaidNarrative;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The paid-narrative lifecycle: a stored narrative is returned unchanged; otherwise one provider call, validation and
 * at most one repair.
 *
 * <ul>
 *   <li>Validated content becomes the canonical, immutable AVAILABLE narrative.</li>
 *   <li>Content that still fails validation after its one repair becomes the canonical, immutable final FALLBACK,
 *       recording the failed rule for diagnostics. It is never retried.</li>
 *   <li>No usable provider response (not configured, timeout, connection failure, rate limit, provider error or an
 *       empty response) is a RETRYABLE_FALLBACK: nothing is stored, so a transient outage never becomes the
 *       canonical result and a later explicit preparation can try again. Only an explicit request retries.</li>
 * </ul>
 *
 * <p>Concurrency, as for the responsibility analysis: two truly simultaneous first requests may both call the
 * provider; the unique assessment constraint keeps one canonical narrative and both callers receive it.</p>
 */
public final class ProfessionalPaidNarrativeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalPaidNarrativeService.class);

    private final ProfessionalPaidNarrativeStore store;
    private final Supplier<Optional<ProfessionalPaidNarrativeGenerator>> generator;
    private final ProfessionalPaidNarrativeValidator validator;
    private final Clock clock;
    private final AssessmentTrace trace;

    public ProfessionalPaidNarrativeService(ProfessionalPaidNarrativeStore store,
            Supplier<Optional<ProfessionalPaidNarrativeGenerator>> generator, ProfessionalPaidNarrativeValidator validator,
            Clock clock, AssessmentTrace trace) {
        this.store = Objects.requireNonNull(store);
        this.generator = Objects.requireNonNull(generator);
        this.validator = Objects.requireNonNull(validator);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    public Optional<StoredProfessionalPaidNarrative> find(UUID assessmentId) {
        return store.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    public PaidNarrativeResult generateOrGet(ProfessionalPaidNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        Optional<StoredProfessionalPaidNarrative> existing = store.findByAssessmentId(context.assessmentId());
        if (existing.isPresent()) {
            var stored = existing.get();
            if (!stored.guidanceId().equals(context.guidanceId()) || !stored.analysisId().equals(context.analysisId())) {
                throw new IllegalStateException("the stored paid narrative explains a different analysis or guidance");
            }
            trace.event(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_REUSED, () -> summary(stored, false));
            return PaidNarrativeResult.stored(stored);
        }
        Optional<ProfessionalPaidNarrativeGenerator> provider = generator.get();
        if (provider.isEmpty()) {
            return retryable(context, "PROVIDER_NOT_CONFIGURED", 0);
        }
        return generate(context, provider.get());
    }

    private PaidNarrativeResult generate(ProfessionalPaidNarrativeContext context,
            ProfessionalPaidNarrativeGenerator provider) {
        trace.event(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_REQUEST, () -> request(context, provider));
        GeneratedPaidNarrative first;
        try {
            first = trace.timed(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_RESPONSE, "paidNarrativeModelMs",
                    () -> provider.generate(context), value -> response(1, value));
        }
        catch (RuntimeException failure) {
            return retryable(context, "PROVIDER_ERROR:" + failure.getClass().getSimpleName(), 1);
        }
        try {
            return save(available(context, provider, first, 1, validator.validate(first.narrative(), context)));
        }
        catch (NarrativeRuleViolation violation) {
            if (!ProfessionalPaidNarrativeValidator.REPAIRABLE.contains(violation.rule())) {
                // No content at all: a provider problem, not prose that failed the contract.
                return retryable(context, "EMPTY_PROVIDER_RESPONSE", 1);
            }
            var feedback = new NarrativeRepairFeedback(violation.rule(), violation.reason(), violation.triggeringText());
            trace.event(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_REPAIR, () -> repair(feedback));
            GeneratedPaidNarrative second;
            try {
                second = trace.timed(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_RESPONSE,
                        "paidNarrativeRepairModelMs", () -> provider.repair(context, feedback), value -> response(2, value));
            }
            catch (RuntimeException failure) {
                return retryable(context, "REPAIR_PROVIDER_ERROR:" + failure.getClass().getSimpleName(), 2);
            }
            try {
                return save(available(context, provider, second, 2, validator.validate(second.narrative(), context)));
            }
            catch (NarrativeRuleViolation repairFailure) {
                if (!ProfessionalPaidNarrativeValidator.REPAIRABLE.contains(repairFailure.rule())) {
                    return retryable(context, "EMPTY_PROVIDER_RESPONSE", 2);
                }
                return save(finalFallback(context, provider.providerIdentifier(), model(provider, second),
                        second.providerRequestId(), "VALIDATION_FAILED_AFTER_REPAIR:" + violation.rule() + ">"
                                + repairFailure.rule()));
            }
        }
    }

    private StoredProfessionalPaidNarrative available(ProfessionalPaidNarrativeContext context,
            ProfessionalPaidNarrativeGenerator provider, GeneratedPaidNarrative generated, int attempts,
            ProfessionalPaidNarrative narrative) {
        return new StoredProfessionalPaidNarrative(UUID.randomUUID(), context.assessmentId(), context.analysisId(),
                context.guidanceId(), ProfessionalPaidNarrativeVersions.CURRENT, PaidNarrativeStatus.AVAILABLE,
                provider.providerIdentifier(), model(provider, generated), generated.providerRequestId(), attempts,
                Optional.empty(), Instant.now(clock), Optional.of(narrative));
    }

    private StoredProfessionalPaidNarrative finalFallback(ProfessionalPaidNarrativeContext context, String provider,
            Optional<String> model, Optional<String> requestId, String reason) {
        var stored = new StoredProfessionalPaidNarrative(UUID.randomUUID(), context.assessmentId(), context.analysisId(),
                context.guidanceId(), ProfessionalPaidNarrativeVersions.CURRENT, PaidNarrativeStatus.FALLBACK, provider,
                model, requestId, 2, Optional.of(reason), Instant.now(clock), Optional.empty());
        LOGGER.warn("Paid narrative journey=professional assessmentId={} outcome=FALLBACK final=true reason={}",
                context.assessmentId(), reason);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_FALLBACK, () -> summary(stored, true));
        return stored;
    }

    private PaidNarrativeResult retryable(ProfessionalPaidNarrativeContext context, String reason, int attempts) {
        LOGGER.warn("Paid narrative journey=professional assessmentId={} outcome=RETRYABLE_FALLBACK reason={} attempts={}",
                context.assessmentId(), reason, attempts);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.PAID_NARRATIVE_FALLBACK, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", PaidNarrativeStatus.RETRYABLE_FALLBACK);
            payload.put("reason", reason);
            payload.put("attempts", attempts);
            payload.put("providerCalled", attempts > 0);
            payload.put("stored", false);
            payload.put("deterministicCopyUsed", true);
            return payload;
        });
        return PaidNarrativeResult.retryable(reason, attempts);
    }

    private PaidNarrativeResult save(StoredProfessionalPaidNarrative candidate) {
        StoredProfessionalPaidNarrative stored = store.saveIfAbsent(candidate);
        if (stored.status() == PaidNarrativeStatus.AVAILABLE) {
            LOGGER.info("Paid narrative journey=professional assessmentId={} outcome=AVAILABLE attempts={}",
                    stored.assessmentId(), stored.attempts());
        }
        return PaidNarrativeResult.stored(stored);
    }

    private static Optional<String> model(ProfessionalPaidNarrativeGenerator provider, GeneratedPaidNarrative generated) {
        return generated.model().or(provider::modelIdentifier);
    }

    private static Map<String, Object> request(ProfessionalPaidNarrativeContext context,
            ProfessionalPaidNarrativeGenerator provider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("narrativeVersion", ProfessionalPaidNarrativeVersions.CURRENT);
        payload.put("provider", provider.providerIdentifier());
        payload.put("model", provider.modelIdentifier().orElse(null));
        payload.put("analysisId", context.analysisId());
        payload.put("guidanceId", context.guidanceId());
        payload.put("opportunityIds", context.opportunityIds());
        payload.put("humanValueIds", context.humanValueIds());
        payload.put("skillIds", context.skillIds());
        payload.put("planActionIds", context.planActionIds());
        payload.put("prompt", context.prompt());
        return payload;
    }

    private static Map<String, Object> response(int attempt, GeneratedPaidNarrative generated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", attempt);
        payload.put("providerRequestId", generated.providerRequestId().orElse(null));
        payload.put("narrative", generated.narrative());
        return payload;
    }

    private static Map<String, Object> repair(NarrativeRepairFeedback feedback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", 2);
        payload.put("failedRule", feedback.rule());
        payload.put("ruleReason", feedback.reason());
        payload.put("offendingText", feedback.offendingSentence());
        payload.put("rejectedDraftStored", false);
        return payload;
    }

    /** {@code justGenerated}: this request produced the outcome, so the provider was called if any attempt was made. */
    private static Map<String, Object> summary(StoredProfessionalPaidNarrative stored, boolean justGenerated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("narrativeId", stored.narrativeId());
        payload.put("narrativeVersion", stored.narrativeVersion());
        payload.put("status", stored.status());
        payload.put("fallbackReason", stored.fallbackReason().orElse(null));
        payload.put("attempts", stored.attempts());
        payload.put("providerCalled", justGenerated && stored.attempts() > 0);
        payload.put("deterministicCopyUsed", stored.status() == PaidNarrativeStatus.FALLBACK);
        payload.put("stored", true);
        return payload;
    }
}
