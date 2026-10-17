package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Counts the handful of things worth knowing about in production, tagged only by journey.
 *
 * <p>Two rules shape this class:</p>
 *
 * <ul>
 *   <li><strong>Low cardinality.</strong> The only tag is the journey, or the rate-limit group;
 *       timers add a fixed operation name and a success or failure outcome. An
 *       assessment id, a profession, a course title or an address as a tag would create an
 *       unbounded number of time series and would also turn a metrics backend into a second,
 *       unaudited copy of user data.</li>
 *   <li><strong>Never load-bearing.</strong> Recording a measurement cannot fail an assessment, a
 *       payment, a report or a PDF. Every method swallows its own failure, because a metrics
 *       backend having a bad day must not become a customer having one.</li>
 * </ul>
 *
 * <p>{@link #disabled()} returns an instance that records nothing, so a collaborator can be built
 * without a registry — in a unit test, or anywhere instrumentation is simply not wanted.</p>
 */
public final class JourneyMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMetrics.class);

    private final MeterRegistry registry;

    public JourneyMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    private JourneyMetrics() {
        this.registry = null;
    }

    /** An instance that records nothing. */
    public static JourneyMetrics disabled() {
        return new JourneyMetrics();
    }

    public void assessmentCreated(String journey) {
        count("assessments.created", "journey", journey);
    }

    public void judgmentFailed(String journey) {
        count("judgment.failures", "journey", journey);
    }

    public void narrativeFailed(String journey) {
        count("narrative.failures", "journey", journey);
    }

    public void evidenceFailed(String journey) {
        count("evidence.failures", "journey", journey);
    }

    public void checkoutFailed(String journey) {
        count("checkout.failures", "journey", journey);
    }

    public void paymentConfirmed(String journey) {
        count("payment.confirmations", "journey", journey);
    }

    public void pdfGenerationFailed(String journey) {
        count("pdf.generation.failures", "journey", journey);
    }

    public void rateLimitRejected(String group) {
        count("rate_limit.rejections", "group", group);
    }

    /**
     * Runs {@code work} and records how long it took as {@code external.calls}, tagged by
     * operation, journey and whether it returned or threw. The work's own result or exception is
     * passed through unchanged; only the measurement is allowed to fail quietly.
     *
     * @param operation a fixed name such as {@code judgment} or {@code checkout}, never user data
     */
    public <T> T timed(String operation, String journey, Supplier<T> work) {
        long started = System.nanoTime();
        String outcome = "failure";
        try {
            T result = work.get();
            outcome = "success";
            return result;
        }
        finally {
            record(operation, journey, outcome, System.nanoTime() - started);
        }
    }

    private void record(String operation, String journey, String outcome, long elapsedNanos) {
        if (registry == null) {
            return;
        }
        try {
            Timer.builder("external.calls")
                    .tag("operation", operation)
                    .tag("journey", journey)
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
        catch (RuntimeException metricsFailure) {
            LOGGER.debug("Could not record external.calls", metricsFailure);
        }
    }

    private void count(String name, String tag, String value) {
        if (registry == null) {
            return;
        }
        try {
            Counter.builder(name).tag(tag, value).register(registry).increment();
        }
        catch (RuntimeException metricsFailure) {
            // Deliberately swallowed, and logged once at debug so it cannot flood a log either.
            LOGGER.debug("Could not record {}", name, metricsFailure);
        }
    }
}
