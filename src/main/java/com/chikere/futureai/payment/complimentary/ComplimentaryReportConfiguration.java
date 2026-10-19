package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.configuration.ConfigurationProblemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Complimentary access is decided by its own flag, never by the active profile, so it behaves the
 * same under dev, dev-no-jev and prod.
 */
@Configuration(proxyBeanMethods = false)
public class ComplimentaryReportConfiguration {

    @Bean
    ComplimentaryReportAccess complimentaryReportAccess(
            @Value("${app.complimentary-report.enabled:false}") boolean enabled,
            @Value("${app.complimentary-report.code:}") String code) {
        if (!enabled) return ComplimentaryReportAccess.disabled();
        if (code == null || code.isBlank()) {
            // Names the setting, never a value: startup failures are pasted into tickets.
            throw new ConfigurationProblemException("Missing required configuration: "
                    + "app.complimentary-report.code (required when app.complimentary-report.enabled=true)");
        }
        return ComplimentaryReportAccess.enabledWith(code);
    }
}
