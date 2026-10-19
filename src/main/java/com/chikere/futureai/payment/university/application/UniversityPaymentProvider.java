package com.chikere.futureai.payment.university.application;

public enum UniversityPaymentProvider {
    STRIPE,
    LOCAL_FIXTURE,
    /**
     * Not a payment gateway: the entitlement was granted with the complimentary access code.
     * It is the same ACTIVE full-report entitlement a payment produces, and no checkout,
     * webhook or payment event is ever associated with it.
     */
    COMPLIMENTARY
}
