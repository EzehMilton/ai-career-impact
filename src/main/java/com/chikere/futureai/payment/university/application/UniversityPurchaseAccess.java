package com.chikere.futureai.payment.university.application;

import java.util.Objects;
import java.util.Optional;

public record UniversityPurchaseAccess(
        Optional<UniversityEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice,
        boolean complimentaryAvailable) {

    public UniversityPurchaseAccess {
        status = Objects.requireNonNull(status);
        displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }

    public UniversityPurchaseAccess(Optional<UniversityEntitlementStatus> status, boolean checkoutAvailable,
            Optional<String> displayPrice) {
        this(status, checkoutAvailable, displayPrice, false);
    }
}
