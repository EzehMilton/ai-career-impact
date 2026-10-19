package com.chikere.futureai.payment.professional.application;

/**
 * The payment provider's own record of a checkout session. A retry on a PENDING purchase is decided
 * by this, never by where the browser came back from.
 */
public enum ProfessionalCheckoutSessionState {
    /** Can still be paid; the visitor is sent back to it rather than to a second session. */
    OPEN,
    /** Payment finished at the provider; only the verified webhook may now activate the purchase. */
    COMPLETE,
    /** Can never be paid, so a replacement session cannot lead to a second charge. */
    EXPIRED
}
