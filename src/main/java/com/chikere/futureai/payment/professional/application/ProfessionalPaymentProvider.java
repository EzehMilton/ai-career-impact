package com.chikere.futureai.payment.professional.application;

public enum ProfessionalPaymentProvider {
    STRIPE,
    LOCAL_FIXTURE,
    /**
     * Not a payment gateway: the entitlement was granted with the complimentary access code.
     * It is the same ACTIVE full-report entitlement a payment produces, and no checkout,
     * webhook or payment event is ever associated with it.
     */
    COMPLIMENTARY
}
