package com.chikere.futureai.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collects the token usage of every billable provider call made while a unit of work runs, and logs
 * one estimated-cost line for it when it finishes.
 *
 * <p>Usage is collected on the current thread. That fits because an assessment's Jev and OpenAI
 * calls all run synchronously on the submitting request's thread; the adapters only need to call
 * {@link #record}, and no port has to carry cost through its return type. A call recorded while no
 * unit of work is open (a live evaluation harness, for example) is ignored.</p>
 *
 * <p>Like {@code JourneyMetrics}, this is never load-bearing: recording and reporting swallow their
 * own failures, and the tracked work's result or exception passes through unchanged. The log line
 * identifies work only by journey and assessment id — never by submitted content.</p>
 */
public final class ProviderCostTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderCostTracker.class);

    private final ProviderCostProperties rates;
    private final ThreadLocal<List<ProviderUsage>> current = new ThreadLocal<>();

    public ProviderCostTracker(ProviderCostProperties rates) {
        this.rates = Objects.requireNonNull(rates, "rates must not be null");
    }

    private ProviderCostTracker() {
        this.rates = null;
    }

    /** An instance that records and logs nothing. */
    public static ProviderCostTracker disabled() {
        return new ProviderCostTracker();
    }

    /** Adds one provider call to the unit of work open on this thread, if there is one. */
    public void record(ProviderUsage usage) {
        try {
            List<ProviderUsage> usages = current.get();
            if (usages != null && usage != null) {
                usages.add(usage);
            }
        }
        catch (RuntimeException trackingFailure) {
            LOGGER.debug("Could not record provider usage", trackingFailure);
        }
    }

    /** Runs a whole assessment submission and logs its estimated cost against the saved assessment id. */
    public UUID trackAssessment(String journey, Supplier<UUID> submission) {
        return track("assessment", journey, submission, UUID::toString);
    }

    /** Runs a CV profile extraction, which happens in its own request before any assessment exists. */
    public <T> T trackCvExtraction(Supplier<T> extraction) {
        return track("cv-extraction", "professional", extraction, ignored -> "none");
    }

    private <T> T track(String operation, String journey, Supplier<T> work, Function<T, String> reference) {
        if (rates == null || current.get() != null) {
            // Disabled, or already inside a tracked unit whose own line will include these calls.
            return work.get();
        }
        List<ProviderUsage> usages = new ArrayList<>();
        current.set(usages);
        T result = null;
        boolean completed = false;
        try {
            result = work.get();
            completed = true;
            return result;
        }
        finally {
            current.remove();
            report(operation, journey, completed ? reference.apply(result) : "none",
                    completed ? "completed" : "failed", usages);
        }
    }

    private void report(String operation, String journey, String assessmentId, String outcome,
            List<ProviderUsage> usages) {
        if (usages.isEmpty()) {
            // Fixture-backed runs make no billable call; a line of zeros would only be noise.
            return;
        }
        try {
            LOGGER.info("Estimated provider cost operation={} journey={} assessmentId={} outcome={} {}",
                    operation, journey, assessmentId, outcome, ProviderCostEstimate.of(usages, rates).logFields());
        }
        catch (RuntimeException reportingFailure) {
            LOGGER.debug("Could not report provider cost", reportingFailure);
        }
    }
}
