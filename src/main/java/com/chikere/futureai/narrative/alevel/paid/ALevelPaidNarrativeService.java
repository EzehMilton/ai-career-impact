package com.chikere.futureai.narrative.alevel.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One paid-narrative attempt: a provider call, validation and at most one repair. Nothing is stored here; the
 * detailed-report lifecycle freezes an AVAILABLE or final FALLBACK outcome into the report snapshot, and keeps a
 * RETRYABLE_FALLBACK provisional, so a transient outage never becomes the canonical result.
 *
 * <ul>
 *   <li>Validated prose is AVAILABLE.</li>
 *   <li>Prose that still fails validation after its one repair is a final FALLBACK.</li>
 *   <li>No usable provider response (not configured, provider error or an empty response) is RETRYABLE_FALLBACK.</li>
 * </ul>
 */
public final class ALevelPaidNarrativeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelPaidNarrativeService.class);

    private final Supplier<Optional<ALevelPaidNarrativeGenerator>> generator;
    private final ALevelPaidNarrativeValidator validator;
    private final AssessmentTrace trace;

    public ALevelPaidNarrativeService(Supplier<Optional<ALevelPaidNarrativeGenerator>> generator,
            ALevelPaidNarrativeValidator validator, AssessmentTrace trace) {
        this.generator = Objects.requireNonNull(generator);
        this.validator = Objects.requireNonNull(validator);
        this.trace = Objects.requireNonNull(trace);
    }

    public ALevelPaidNarrativeOutcome generate(ALevelPaidNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        Optional<ALevelPaidNarrativeGenerator> provider = generator.get();
        if (provider.isEmpty()) {
            return retryable(context, "none", Optional.empty(), "PROVIDER_NOT_CONFIGURED", 0);
        }
        ALevelPaidNarrativeGenerator active = provider.get();
        trace.event(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_REQUEST, () -> request(context, active));
        ALevelPaidNarrativeGenerator.GeneratedNarrative first;
        try {
            first = trace.timed(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_RESPONSE, "paidNarrativeModelMs",
                    () -> active.generate(context), value -> response(1, value));
        }
        catch (RuntimeException failure) {
            return retryable(context, active.providerIdentifier(), active.modelIdentifier(),
                    "PROVIDER_ERROR:" + failure.getClass().getSimpleName(), 1);
        }
        try {
            return available(active, first, 1, validator.validate(first.narrative(), context));
        }
        catch (NarrativeRuleViolation violation) {
            if (!ALevelPaidNarrativeValidator.REPAIRABLE.contains(violation.rule())) {
                return retryable(context, active.providerIdentifier(), model(active, first), "EMPTY_PROVIDER_RESPONSE", 1);
            }
            var feedback = new ALevelPaidNarrativeGenerator.RepairFeedback(violation.rule(), violation.reason(),
                    violation.triggeringText());
            trace.event(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_REPAIR, () -> Map.of(
                    "attempt", 2, "failedRule", violation.rule(), "rejectedDraftStored", false));
            ALevelPaidNarrativeGenerator.GeneratedNarrative second;
            try {
                second = trace.timed(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_RESPONSE,
                        "paidNarrativeRepairModelMs", () -> active.repair(context, feedback), value -> response(2, value));
            }
            catch (RuntimeException failure) {
                return retryable(context, active.providerIdentifier(), model(active, first),
                        "REPAIR_PROVIDER_ERROR:" + failure.getClass().getSimpleName(), 2);
            }
            try {
                return available(active, second, 2, validator.validate(second.narrative(), context));
            }
            catch (NarrativeRuleViolation repairFailure) {
                if (!ALevelPaidNarrativeValidator.REPAIRABLE.contains(repairFailure.rule())) {
                    return retryable(context, active.providerIdentifier(), model(active, second),
                            "EMPTY_PROVIDER_RESPONSE", 2);
                }
                String reason = "VALIDATION_FAILED_AFTER_REPAIR:" + violation.rule() + ">" + repairFailure.rule();
                LOGGER.warn("Paid narrative journey=alevel assessmentId={} outcome=FALLBACK final=true reason={}",
                        context.assessmentId(), reason);
                trace.event(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_FALLBACK, () -> Map.of(
                        "status", ALevelPaidNarrativeStatus.FALLBACK, "reason", reason, "attempts", 2,
                        "deterministicCopyUsed", true));
                return new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(),
                        ALevelPaidNarrativeVersions.CURRENT, active.providerIdentifier(), model(active, second),
                        second.providerRequestId(), 2, Optional.of(reason));
            }
        }
    }

    private ALevelPaidNarrativeOutcome available(ALevelPaidNarrativeGenerator provider,
            ALevelPaidNarrativeGenerator.GeneratedNarrative generated, int attempts,
            ALevelPaidNarrative narrative) {
        return new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.AVAILABLE, Optional.of(narrative),
                ALevelPaidNarrativeVersions.CURRENT, provider.providerIdentifier(), model(provider, generated),
                generated.providerRequestId(), attempts, Optional.empty());
    }

    private ALevelPaidNarrativeOutcome retryable(ALevelPaidNarrativeContext context, String provider,
            Optional<String> model, String reason, int attempts) {
        LOGGER.warn("Paid narrative journey=alevel assessmentId={} outcome=RETRYABLE_FALLBACK reason={} attempts={}",
                context.assessmentId(), reason, attempts);
        trace.event(JourneyType.A_LEVEL, TraceStage.PAID_NARRATIVE_FALLBACK, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK);
            payload.put("reason", reason);
            payload.put("attempts", attempts);
            payload.put("stored", false);
            payload.put("deterministicCopyUsed", true);
            return payload;
        });
        return new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK, Optional.empty(),
                ALevelPaidNarrativeVersions.CURRENT, provider, model, Optional.empty(), attempts, Optional.of(reason));
    }

    private static Optional<String> model(ALevelPaidNarrativeGenerator provider,
            ALevelPaidNarrativeGenerator.GeneratedNarrative generated) {
        return generated.model().or(provider::modelIdentifier);
    }

    /** The provider's answer as received, before validation; the rejected draft of a repair is never stored. */
    private static Map<String, Object> response(int attempt, ALevelPaidNarrativeGenerator.GeneratedNarrative generated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", attempt);
        payload.put("providerRequestId", generated.providerRequestId().orElse(null));
        payload.put("model", generated.model().orElse(null));
        payload.put("narrative", generated.narrative());
        return payload;
    }

    private static Map<String, Object> request(ALevelPaidNarrativeContext context,
            ALevelPaidNarrativeGenerator provider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("narrativeVersion", ALevelPaidNarrativeVersions.CURRENT);
        payload.put("provider", provider.providerIdentifier());
        payload.put("model", provider.modelIdentifier().orElse(null));
        payload.put("prompt", context.prompt());
        return payload;
    }
}
