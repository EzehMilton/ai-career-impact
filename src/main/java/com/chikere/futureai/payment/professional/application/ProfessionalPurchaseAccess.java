package com.chikere.futureai.payment.professional.application;

import java.util.Optional;

public record ProfessionalPurchaseAccess(
        Optional<ProfessionalEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice) {
    public ProfessionalPurchaseAccess {
        status = java.util.Objects.requireNonNull(status);
        displayPrice = java.util.Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }
}
