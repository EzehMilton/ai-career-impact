package com.chikere.futureai.payment.alevel.application;

import java.util.Objects;
import java.util.Optional;

public record ALevelPurchaseAccess(
        Optional<ALevelEntitlementStatus> status,
        boolean checkoutAvailable,
        Optional<String> displayPrice) {

    public ALevelPurchaseAccess {
        status = Objects.requireNonNull(status);
        displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
    }
}
