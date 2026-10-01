package com.chikere.futureai.payment.alevel.stripe;

/** Metadata keys that mark a Checkout Session as belonging to the A-level product. */
final class ALevelStripeMetadata {
    static final String PRODUCT_KEY = "product";
    static final String PRODUCT_VALUE = "a_level_report";
    static final String ASSESSMENT_ID = "assessment_id";
    static final String ENTITLEMENT_ID = "entitlement_id";

    private ALevelStripeMetadata() { }
}
