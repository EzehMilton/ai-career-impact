package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Counters are observational. These tests hold the two properties that make them safe to leave in
 * production code: they cannot fail anything, and they cannot grow without bound.
 */
class JourneyMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final JourneyMetrics metrics = new JourneyMetrics(registry);

    @Test
    void eachJourneyCountsSeparatelyAndTagsNothingButTheJourney() {
        metrics.assessmentCreated("professional");
        metrics.assessmentCreated("university");
        metrics.assessmentCreated("university");

        assertThat(count("assessments.created", "journey", "professional")).isEqualTo(1);
        assertThat(count("assessments.created", "journey", "university")).isEqualTo(2);
        assertThat(registry.find("assessments.created").meters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .singleElement()
                        .satisfies(tag -> assertThat(tag.getKey()).isEqualTo("journey")));
    }

    @Test
    void everyFailureKindHasItsOwnCounter() {
        metrics.narrativeFailed("a-level");
        metrics.evidenceFailed("a-level");
        metrics.checkoutFailed("a-level");
        metrics.pdfGenerationFailed("a-level");
        metrics.paymentConfirmed("a-level");
        metrics.rateLimitRejected("cv-upload");

        assertThat(count("narrative.failures", "journey", "a-level")).isEqualTo(1);
        assertThat(count("evidence.failures", "journey", "a-level")).isEqualTo(1);
        assertThat(count("checkout.failures", "journey", "a-level")).isEqualTo(1);
        assertThat(count("pdf.generation.failures", "journey", "a-level")).isEqualTo(1);
        assertThat(count("payment.confirmations", "journey", "a-level")).isEqualTo(1);
        assertThat(count("rate_limit.rejections", "group", "cv-upload")).isEqualTo(1);
    }

    @Test
    void theCardinalityOfEveryMeterStaysBoundedByTheNumberOfJourneys() {
        // Recording the same journey repeatedly must not create a new time series each time. An
        // identifier or a job title as a tag would, which is exactly what this guards against.
        for (int attempt = 0; attempt < 500; attempt++) {
            metrics.assessmentCreated("professional");
        }

        assertThat(registry.find("assessments.created").meters()).hasSize(1);
    }

    @Test
    void aDisabledInstanceRecordsNothingAndStillWorks() {
        JourneyMetrics disabled = JourneyMetrics.disabled();

        assertThatCode(() -> {
            disabled.assessmentCreated("professional");
            disabled.paymentConfirmed("university");
            disabled.rateLimitRejected("checkout-start");
        }).doesNotThrowAnyException();
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void aBrokenRegistryCannotBreakTheBusinessOperationItIsMeasuring() {
        // If recording a measurement could throw, a metrics backend having a bad day would become
        // a customer losing an assessment.
        MeterRegistry broken = new SimpleMeterRegistry() {
            @Override
            protected io.micrometer.core.instrument.Counter newCounter(io.micrometer.core.instrument.Meter.Id id) {
                throw new IllegalStateException("registry is unavailable");
            }
        };

        assertThatCode(() -> new JourneyMetrics(broken).assessmentCreated("professional"))
                .doesNotThrowAnyException();
    }

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }
}
