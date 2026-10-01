package com.chikere.futureai.payment.university.persistence;

import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;

import java.io.Serializable;
import java.util.Objects;

final class ProcessedUniversityPaymentEventId implements Serializable {
    private UniversityPaymentProvider paymentProvider;
    private String providerEventId;

    public ProcessedUniversityPaymentEventId() { }

    ProcessedUniversityPaymentEventId(UniversityPaymentProvider paymentProvider, String providerEventId) {
        this.paymentProvider = paymentProvider;
        this.providerEventId = providerEventId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProcessedUniversityPaymentEventId that)) return false;
        return paymentProvider == that.paymentProvider && Objects.equals(providerEventId, that.providerEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentProvider, providerEventId);
    }
}
