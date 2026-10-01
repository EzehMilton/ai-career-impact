package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;

import java.io.Serializable;
import java.util.Objects;

final class ProcessedALevelPaymentEventId implements Serializable {
    private ALevelPaymentProvider paymentProvider;
    private String providerEventId;

    public ProcessedALevelPaymentEventId() { }

    ProcessedALevelPaymentEventId(ALevelPaymentProvider paymentProvider, String providerEventId) {
        this.paymentProvider = paymentProvider;
        this.providerEventId = providerEventId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProcessedALevelPaymentEventId that)) return false;
        return paymentProvider == that.paymentProvider && Objects.equals(providerEventId, that.providerEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentProvider, providerEventId);
    }
}
