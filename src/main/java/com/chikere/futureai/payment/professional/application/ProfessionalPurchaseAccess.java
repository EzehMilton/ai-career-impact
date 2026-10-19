package com.chikere.futureai.payment.professional.application;

import java.util.Optional;

public record ProfessionalPurchaseAccess(
        Optional<ProfessionalEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice,
        boolean complimentaryAvailable) {
    public ProfessionalPurchaseAccess {
        status = java.util.Objects.requireNonNull(status);
        displayPrice = java.util.Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }

    public ProfessionalPurchaseAccess(Optional<ProfessionalEntitlementStatus> status, boolean checkoutAvailable,
            Optional<String> displayPrice) {
        this(status, checkoutAvailable, displayPrice, false);
    }
}
