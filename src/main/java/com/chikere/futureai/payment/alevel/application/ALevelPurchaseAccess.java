package com.chikere.futureai.payment.alevel.application;

import java.util.Objects;
import java.util.Optional;

public record ALevelPurchaseAccess(
        Optional<ALevelEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice,
        boolean complimentaryAvailable) {

    public ALevelPurchaseAccess {
        status = Objects.requireNonNull(status);
        displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }

    public ALevelPurchaseAccess(Optional<ALevelEntitlementStatus> status, boolean checkoutAvailable,
            Optional<String> displayPrice) {
        this(status, checkoutAvailable, displayPrice, false);
    }
}
