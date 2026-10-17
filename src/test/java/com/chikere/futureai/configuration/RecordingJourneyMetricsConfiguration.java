package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Gives a sliced test real, in-memory {@link JourneyMetrics}, so it can assert what was counted by
 * reading the registry. JourneyMetrics is final and deliberately not mocked.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingJourneyMetricsConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    JourneyMetrics journeyMetrics(MeterRegistry registry) {
        return new JourneyMetrics(registry);
    }
}
