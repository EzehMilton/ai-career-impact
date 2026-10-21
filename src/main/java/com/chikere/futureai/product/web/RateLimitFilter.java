package com.chikere.futureai.product.web;

import com.chikere.futureai.configuration.JourneyMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies a rate limit to the few operations that cost real work: starting an assessment and
 * creating a checkout. Reading a page, a past result or a stylesheet is never limited —
 * those are cheap, and someone re-reading their own result is not abuse.
 *
 * <p>This filter sits <em>after</em> Spring Security in the chain, which matters: a POST with a
 * missing or stale CSRF token is rejected as 403 before it reaches here, so a broken form never
 * spends someone's allowance and never turns into a confusing 429. The cost of that ordering is
 * that unauthenticated flooding without a CSRF token is stopped by the security filter instead of
 * being counted here, which is the cheaper rejection anyway.</p>
 *
 * <p>Stripe webhooks are never limited. They are signature-verified, and Stripe retries, so
 * throttling them would only delay a payment confirmation that is already proven genuine.</p>
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

    /** A named group of routes that share one allowance, so one limit cannot be dodged by alternating paths. */
    private record Group(String name, List<String> suffixes) {
        boolean matches(String path) {
            return suffixes.stream().anyMatch(path::endsWith);
        }
    }

    private static final List<Group> GROUPS = List.of(
            new Group("assessment-submission", List.of("-assessment")),
            new Group("checkout-start", List.of("/checkout")));

    private final Map<String, RequestRateLimiter> limiters;
    private final JourneyMetrics metrics;

    public RateLimitFilter(Map<String, RequestRateLimiter> limiters, JourneyMetrics metrics) {
        this.limiters = Map.copyOf(limiters);
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Optional<Group> group = groupFor(request);
        if (group.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RequestRateLimiter limiter = limiters.get(group.get().name());
        if (limiter == null) {
            chain.doFilter(request, response);
            return;
        }
        RequestRateLimiter.Decision decision = limiter.check(group.get().name() + "|" + clientKey(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        // The group, not the path or the client, so the log says what was throttled without
        // recording who or what they submitted.
        LOGGER.warn("Rate limit reached for {}", group.get().name());
        metrics.rateLimitRejected(group.get().name());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.sendError(429);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only ever applies to writes, so no page or asset can be throttled by accident.
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    private Optional<Group> groupFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/payments/stripe/")) {
            return Optional.empty();
        }
        // Most specific first.
        return GROUPS.stream()
                .sorted((left, right) -> Integer.compare(longest(right), longest(left)))
                .filter(group -> group.matches(path))
                .findFirst();
    }

    private int longest(Group group) {
        return group.suffixes().stream().mapToInt(String::length).max().orElse(0);
    }

    /**
     * The client's address as the container reports it. Forwarded headers are deliberately not
     * consulted: nothing configures a trusted proxy yet, so {@code X-Forwarded-For} is attacker
     * controlled and would let one client present itself as thousands. Behind an unconfigured
     * proxy this makes the limit coarse rather than wrong, which is the safer way to be imprecise.
     */
    private String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
