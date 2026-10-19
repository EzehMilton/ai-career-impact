package com.chikere.futureai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * This product has no accounts, so no route is protected by authentication. Access to a paid report
 * or its PDF is decided by an ACTIVE entitlement, checked in the application layer, and that is the
 * only thing standing between a visitor and paid content. The filter chain's job here is therefore
 * narrow: publish the routes the product serves, keep CSRF on browser POSTs, and let anything else
 * fall through to Spring MVC so an unknown URL becomes an ordinary 404.
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {

    /**
     * The only JavaScript is the first-party payment-confirmation poller served from /js/, so
     * scripts are limited to this origin with no inline or eval allowance, and there are no inline
     * styles. Its status requests fall under {@code default-src 'self'}. {@code form-action} names
     * Stripe because paying leaves the site through a redirect that follows a form submission.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'none'",
            "form-action 'self' https://checkout.stripe.com");

    private static final String PERMISSIONS_POLICY = String.join(", ",
            "accelerometer=()", "camera=()", "geolocation=()", "gyroscope=()",
            "magnetometer=()", "microphone=()", "payment=()", "usb=()");

    /**
     * There are no accounts, and this bean exists to keep it that way.
     *
     * <p>Spring Boot creates an in-memory user and writes its generated password into the startup
     * log whenever the context contains no authentication bean. Declaring a {@code SecurityFilterChain}
     * does not satisfy that condition — the application was printing a password on every start with
     * this chain already in place. Supplying a service that can never find a user backs the
     * auto-configuration off from inside the security configuration, which is a smaller change than
     * excluding it at the application class, and makes the intent explicit at the same time: lookups
     * do not fail because the directory is empty, they fail because there is no directory.</p>
     */
    @Bean
    UserDetailsService noAccountsExist() {
        return username -> {
            throw new UsernameNotFoundException("This application has no user accounts");
        };
    }

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                // Product shell: landing page, informational pages and the error view.
                .requestMatchers(HttpMethod.GET, "/", "/methodology", "/privacy").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/professional-assessment", "/professional-assessment/*",
                        "/professional-assessment/cv/review", "/professional-assessment/cv/edit",
                        "/professional-assessment/*/report", "/professional-assessment/*/checkout/local",
                        "/professional-assessment/*/report.pdf",
                        "/university-assessment", "/university-assessment/*",
                        "/university-assessment/*/report", "/university-assessment/*/checkout/local",
                        "/university-assessment/*/report.pdf",
                        "/a-level-assessment", "/a-level-assessment/*",
                        "/a-level-assessment/*/report", "/a-level-assessment/*/checkout/local",
                        "/a-level-assessment/*/report.pdf",
                        "/professional-assessment/*/payment-status", "/university-assessment/*/payment-status",
                        "/a-level-assessment/*/payment-status",
                        "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.POST, "/professional-assessment",
                        "/professional-assessment/cv",
                        "/professional-assessment/cv/edit", "/professional-assessment/cv/confirm",
                        "/professional-assessment/*/checkout",
                        "/professional-assessment/*/checkout/local/confirm",
                        "/professional-assessment/*/complimentary-access",
                        "/university-assessment",
                        "/university-assessment/*/checkout",
                        "/university-assessment/*/checkout/local/confirm",
                        "/university-assessment/*/complimentary-access",
                        "/a-level-assessment",
                        "/a-level-assessment/*/checkout",
                        "/a-level-assessment/*/checkout/local/confirm",
                        "/a-level-assessment/*/complimentary-access",
                        "/payments/stripe/webhook",
                        "/payments/stripe/university-webhook",
                        "/payments/stripe/a-level-webhook").permitAll()
                // Operational endpoints. Health and its probes are published because a load
                // balancer and an orchestrator have to reach them before anyone is signed in, and
                // they are configured to report a bare status with no component detail. Everything
                // else Actuator can expose stays denied here as well as unexposed, so publishing
                // one by accident in configuration is not enough to make it reachable.
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                        "/actuator/health/readiness", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                // Everything else reaches Spring MVC, which answers an unknown URL with an ordinary
                // 404 and the friendly not-found page. This does not open anything: no route in this
                // application is guarded by authentication, and paid reports and PDFs stay protected
                // by the ACTIVE entitlement check inside their controllers. ProductRouteSecurityTest
                // and the three paid-report and PDF controller tests hold that line.
                .anyRequest().permitAll())
                // Only the Stripe webhook paths are CSRF-exempt: they are authenticated by Stripe's
                // signature instead. Every browser POST keeps CSRF protection, and a missing or
                // stale token stays a 403 rather than being disguised as a missing page.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/payments/stripe/webhook",
                        "/payments/stripe/university-webhook",
                        "/payments/stripe/a-level-webhook"))
                // No form login, no HTTP Basic and no logout: there is nothing to sign in to, so a
                // login page would be a dead end that also implies accounts exist.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .frameOptions(frame -> frame.deny())
                        // Spring Security writes no-store on every response, which is right for a
                        // page that may show someone's assessment and wrong for the one stylesheet
                        // the whole site shares. The writer is scoped rather than switched off, so
                        // the secure default still applies everywhere it matters.
                        .cacheControl(cache -> cache.disable())
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(
                                        PathPatternRequestMatcher.withDefaults().matcher("/css/**")),
                                new CacheControlHeadersWriter())));
        return http.build();
    }
}
