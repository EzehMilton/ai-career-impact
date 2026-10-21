package com.chikere.futureai.product.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window counter, held in memory, for the handful of operations that are expensive enough
 * to be worth protecting.
 *
 * <p>Per instance, deliberately. A shared limiter would mean Redis, and the thing being defended
 * against here — one client repeatedly submitting assessments or starting checkouts — is adequately
 * blunted by a per-process limit. With more than one instance behind a load balancer the effective
 * limit is the configured one multiplied by the instance count, which is a known and documented
 * property rather than a surprise.</p>
 *
 * <p>A fixed window rather than a token bucket because it can answer "how long until you may try
 * again" exactly, which is what {@code Retry-After} is supposed to mean.</p>
 */
public final class RequestRateLimiter {

    /** The outcome of asking whether one more request is allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision permit() {
            return new Decision(true, 0);
        }
    }

    private record Window(Instant startedAt, AtomicInteger count) { }

    /** Beyond this many tracked keys, expired windows are swept before a new one is created. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int permits;
    private final Duration window;
    private final Clock clock;

    public RequestRateLimiter(int permits, Duration window, Clock clock) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be positive");
        }
        if (Objects.requireNonNull(window, "window must not be null").isZero()
                || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.permits = permits;
        this.window = window;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Decision check(String key) {
        Objects.requireNonNull(key, "key must not be null");
        Instant now = clock.instant();
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(tracked -> expired(tracked, now));
        }
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || expired(existing, now)
                        ? new Window(now, new AtomicInteger())
                        : existing);
        if (current.count().incrementAndGet() <= permits) {
            return Decision.permit();
        }
        Duration remaining = window.minus(Duration.between(current.startedAt(), now));
        return new Decision(false, Math.max(1, remaining.toSeconds()));
    }

    private boolean expired(Window tracked, Instant now) {
        return !Duration.between(tracked.startedAt(), now).minus(window).isNegative();
    }
}
