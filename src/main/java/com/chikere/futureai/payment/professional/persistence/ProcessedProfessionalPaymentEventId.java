package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;

import java.io.Serializable;
import java.util.Objects;

final class ProcessedProfessionalPaymentEventId implements Serializable {
    private ProfessionalPaymentProvider paymentProvider;
    private String providerEventId;

    public ProcessedProfessionalPaymentEventId() { }

    ProcessedProfessionalPaymentEventId(ProfessionalPaymentProvider paymentProvider, String providerEventId) {
        this.paymentProvider = paymentProvider;
        this.providerEventId = providerEventId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProcessedProfessionalPaymentEventId that)) return false;
        return paymentProvider == that.paymentProvider && Objects.equals(providerEventId, that.providerEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentProvider, providerEventId);
    }
}
