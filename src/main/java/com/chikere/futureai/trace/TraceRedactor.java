package com.chikere.futureai.trace;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A last line of defence applied to every rendered trace event.
 *
 * <p>The trace is built from assessment data, prompts, responses and non-secret configuration, so a
 * credential should never reach it in the first place. This catches the shapes a credential takes if
 * one ever does, for example inside a provider's exception message.</p>
 */
final class TraceRedactor {

    static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SECRETS = List.of(
            // OpenAI and similar provider keys.
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}"),
            // Stripe secret, restricted and publishable-with-secret keys, and webhook secrets.
            Pattern.compile("\\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]+"),
            Pattern.compile("\\bwhsec_[A-Za-z0-9]+"),
            // Authorization header values.
            Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}"));

    /** Assignments such as {@code api-key=...} or {@code "password": "..."}; only the value is replaced. */
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)(\"?(?:api[-_]?key|apikey|password|secret|authorization|access[-_]?code|cookie)\"?\\s*[:=]\\s*\"?)"
                    + "([^\"\\s,}]+)");

    private TraceRedactor() {
    }

    static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = text;
        for (Pattern secret : SECRETS) {
            redacted = secret.matcher(redacted).replaceAll(REDACTED);
        }
        return ASSIGNED_SECRET.matcher(redacted).replaceAll("$1" + REDACTED);
    }
}
