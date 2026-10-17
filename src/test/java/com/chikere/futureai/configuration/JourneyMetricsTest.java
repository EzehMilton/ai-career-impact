package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        metrics.judgmentFailed("a-level");
        metrics.narrativeFailed("a-level");
        metrics.evidenceFailed("a-level");
        metrics.checkoutFailed("a-level");
        metrics.pdfGenerationFailed("a-level");
        metrics.paymentConfirmed("a-level");
        metrics.rateLimitRejected("cv-upload");

        assertThat(count("judgment.failures", "journey", "a-level")).isEqualTo(1);
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

    @Test
    void aTimedCallReturnsItsResultAndIsRecordedAsASuccess() {
        String result = metrics.timed("judgment", "professional", () -> "judged");

        assertThat(result).isEqualTo("judged");
        assertThat(registry.get("external.calls").tag("operation", "judgment").tag("journey", "professional")
                .tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void aTimedCallThatThrowsIsRecordedAsAFailureAndTheSameExceptionPropagates() {
        IllegalStateException providerFailure = new IllegalStateException("provider unavailable");

        assertThatThrownBy(() -> metrics.timed("checkout", "university", () -> {
            throw providerFailure;
        })).isSameAs(providerFailure);
        assertThat(registry.get("external.calls").tag("operation", "checkout").tag("journey", "university")
                .tag("outcome", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void aBrokenRegistryCannotBreakATimedCall() {
        MeterRegistry broken = new SimpleMeterRegistry() {
            @Override
            protected io.micrometer.core.instrument.Timer newTimer(io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config,
                    io.micrometer.core.instrument.distribution.pause.PauseDetector pauseDetector) {
                throw new IllegalStateException("registry is unavailable");
            }
        };

        assertThat(new JourneyMetrics(broken).timed("narrative", "a-level", () -> 42)).isEqualTo(42);
        assertThat(JourneyMetrics.disabled().timed("narrative", "a-level", () -> 42)).isEqualTo(42);
    }

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }
}
