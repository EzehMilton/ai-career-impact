package com.chikere.futureai.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Describes a failure for a log line by the class names of its cause chain, outermost first, for
 * example {@code PaymentUnavailableException<-ApiConnectionException}.
 *
 * <p>Application exceptions wrap the real failure, so the outer class alone says "checkout failed"
 * for an authentication error, a misconfigured price and a network timeout alike. The chain tells
 * them apart. Only class names are used, never messages: a message from a library or a provider
 * may echo request content, and no log line should carry that.</p>
 */
public final class FailureDescription {

    /** Bounds the description even for a pathological or deliberately long chain. */
    private static final int MAXIMUM_DEPTH = 5;

    private FailureDescription() {
    }

    public static String of(Throwable failure) {
        List<String> names = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && names.size() < MAXIMUM_DEPTH
                && seen.add(current); current = current.getCause()) {
            names.add(current.getClass().getSimpleName());
        }
        return String.join("<-", names);
    }
}
