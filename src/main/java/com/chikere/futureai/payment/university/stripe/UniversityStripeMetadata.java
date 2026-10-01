package com.chikere.futureai.payment.university.stripe;

/** Metadata keys that mark a Checkout Session as belonging to the university product. */
final class UniversityStripeMetadata {
    static final String PRODUCT_KEY = "product";
    static final String PRODUCT_VALUE = "university_report";
    static final String ASSESSMENT_ID = "assessment_id";
    static final String ENTITLEMENT_ID = "entitlement_id";

    private UniversityStripeMetadata() { }
}
