package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseAccess;

public record ALevelPurchaseViewModel(
        boolean noEntitlement,
        boolean pending,
        boolean active,
        boolean denied,
        boolean checkoutAvailable,
        String displayPrice,
        boolean complimentaryAvailable) {

    public static ALevelPurchaseViewModel from(ALevelPurchaseAccess access) {
        var status = access.status();
        return new ALevelPurchaseViewModel(status.isEmpty(),
                status.filter(value -> value == ALevelEntitlementStatus.PENDING).isPresent(),
                status.filter(value -> value == ALevelEntitlementStatus.ACTIVE).isPresent(),
                status.filter(value -> value == ALevelEntitlementStatus.REFUNDED
                        || value == ALevelEntitlementStatus.REVOKED).isPresent(),
                access.checkoutAvailable(), access.displayPrice().orElse(null),
                access.complimentaryAvailable());
    }
}
