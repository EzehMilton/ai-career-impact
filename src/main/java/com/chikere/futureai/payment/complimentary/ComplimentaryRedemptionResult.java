package com.chikere.futureai.payment.complimentary;

public enum ComplimentaryRedemptionResult {
    /** A new ACTIVE entitlement and frozen full report were created. */
    UNLOCKED,
    /** The assessment already had ACTIVE full-report access, however it was obtained. Nothing changed. */
    ALREADY_ACTIVE,
    INVALID_CODE,
    /** A payment is in progress, or access was refunded or revoked, so a code does not override it. */
    NOT_ELIGIBLE,
    /** Complimentary access is switched off; callers answer as if the route did not exist. */
    DISABLED
}
