package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPurchaseAccess;

public record UniversityPurchaseViewModel(
        boolean noEntitlement,
        boolean pending,
        boolean active,
        boolean denied,
        boolean checkoutAvailable,
        String displayPrice,
        boolean complimentaryAvailable) {

    public static UniversityPurchaseViewModel from(UniversityPurchaseAccess access) {
        var status = access.status();
        return new UniversityPurchaseViewModel(status.isEmpty(),
                status.filter(value -> value == UniversityEntitlementStatus.PENDING).isPresent(),
                status.filter(value -> value == UniversityEntitlementStatus.ACTIVE).isPresent(),
                status.filter(value -> value == UniversityEntitlementStatus.REFUNDED
                        || value == UniversityEntitlementStatus.REVOKED).isPresent(),
                access.checkoutAvailable(), access.displayPrice().orElse(null),
                access.complimentaryAvailable());
    }
}
