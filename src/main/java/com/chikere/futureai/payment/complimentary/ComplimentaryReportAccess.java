package com.chikere.futureai.payment.complimentary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The one shared access code that unlocks the ordinary full report without payment.
 *
 * <p>Only a SHA-256 digest of the configured code is kept, so the code itself never sits in a field
 * that could be logged, rendered or serialised. Comparing two fixed-length digests with
 * {@link MessageDigest#isEqual} takes the same time whatever was submitted. There is deliberately no
 * redemption limit: while enabled, the code may unlock any number of different assessments.</p>
 */
public final class ComplimentaryReportAccess {
    private static final ComplimentaryReportAccess DISABLED = new ComplimentaryReportAccess(false, null);

    private final boolean enabled;
    private final byte[] codeDigest;

    private ComplimentaryReportAccess(boolean enabled, byte[] codeDigest) {
        this.enabled = enabled;
        this.codeDigest = codeDigest;
    }

    public static ComplimentaryReportAccess disabled() {
        return DISABLED;
    }

    public static ComplimentaryReportAccess enabledWith(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("a complimentary access code must not be blank");
        }
        return new ComplimentaryReportAccess(true, digest(code.strip()));
    }

    public boolean enabled() {
        return enabled;
    }

    /** Whether the submitted code is the configured one. Always false while disabled. */
    public boolean accepts(String submittedCode) {
        if (!enabled || submittedCode == null || submittedCode.isBlank()) return false;
        return MessageDigest.isEqual(codeDigest, digest(submittedCode.strip()));
    }

    @Override
    public String toString() {
        return "ComplimentaryReportAccess[enabled=" + enabled + "]";
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", unavailable);
        }
    }
}
