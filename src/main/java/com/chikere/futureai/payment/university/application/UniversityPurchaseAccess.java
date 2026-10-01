package com.chikere.futureai.payment.university.application;

import java.util.Objects;
import java.util.Optional;

public record UniversityPurchaseAccess(
        Optional<UniversityEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice) {

    public UniversityPurchaseAccess {
        status = Objects.requireNonNull(status);
        displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }
}
