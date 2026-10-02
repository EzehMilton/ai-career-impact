package com.chikere.futureai.product.web;

import com.chikere.futureai.configuration.JourneyMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the two request-scoped filters and fixes their position relative to Spring Security.
 *
 * <p>Order is part of the behaviour here, not an implementation detail:</p>
 * <ul>
 *   <li>correlation runs first, so a request that is rejected by security is still logged with an
 *       identifier and still returns one;</li>
 *   <li>rate limiting runs after security, so a CSRF rejection stays a 403 and never consumes an
 *       allowance or surfaces as a 429.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class RequestHardeningConfiguration {

    /**
     * Spring Security registers its chain at a negative order, so any non-negative order places a
     * filter after it. RateLimitOrderingTest proves the consequence that actually matters — that a
     * CSRF rejection is still a 403 — rather than trusting this number.
     */
    private static final int AFTER_SPRING_SECURITY = 0;

    @Bean
    FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        var registration = new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.window:PT1M}") Duration window,
            @Value("${app.rate-limit.assessment-submission.permits:10}") int assessmentPermits,
            @Value("${app.rate-limit.cv-upload.permits:5}") int cvUploadPermits,
            @Value("${app.rate-limit.checkout-start.permits:10}") int checkoutPermits,
            Clock clock, JourneyMetrics metrics) {
        Map<String, RequestRateLimiter> limiters = new LinkedHashMap<>();
        limiters.put("assessment-submission", new RequestRateLimiter(assessmentPermits, window, clock));
        limiters.put("cv-upload", new RequestRateLimiter(cvUploadPermits, window, clock));
        limiters.put("checkout-start", new RequestRateLimiter(checkoutPermits, window, clock));

        var registration = new FilterRegistrationBean<>(new RateLimitFilter(limiters, metrics));
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(enabled);
        return registration;
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
