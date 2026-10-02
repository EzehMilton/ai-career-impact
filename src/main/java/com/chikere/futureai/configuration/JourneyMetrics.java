package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Counts the handful of things worth knowing about in production, tagged only by journey.
 *
 * <p>Two rules shape this class:</p>
 *
 * <ul>
 *   <li><strong>Low cardinality.</strong> The only tag is the journey, or the rate-limit group. An
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
