package com.chikere.futureai.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the application's counters.
 *
 * <p>The registry is resolved through an {@link ObjectProvider} so that a sliced test context with
 * no Micrometer registry still starts, falling back to an instance that records nothing. The
 * fallback is also the safe direction: missing instrumentation is an inconvenience, a failed
 * assessment is not.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    @Bean
    JourneyMetrics journeyMetrics(ObjectProvider<MeterRegistry> registry) {
        MeterRegistry resolved = registry.getIfAvailable();
        return resolved == null ? JourneyMetrics.disabled() : new JourneyMetrics(resolved);
    }
}
