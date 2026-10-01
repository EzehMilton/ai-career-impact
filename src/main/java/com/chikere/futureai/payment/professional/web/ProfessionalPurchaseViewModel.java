package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.*;

public record ProfessionalPurchaseViewModel(
        boolean noEntitlement,
        boolean pending,
        boolean active,
        boolean denied,
        boolean checkoutAvailable,
        String displayPrice) {

    public static ProfessionalPurchaseViewModel from(ProfessionalPurchaseAccess access) {
        var status = access.status();
        return new ProfessionalPurchaseViewModel(status.isEmpty(),
                status.filter(value -> value == ProfessionalEntitlementStatus.PENDING).isPresent(),
                status.filter(value -> value == ProfessionalEntitlementStatus.ACTIVE).isPresent(),
                status.filter(value -> value == ProfessionalEntitlementStatus.REFUNDED
                        || value == ProfessionalEntitlementStatus.REVOKED).isPresent(),
                access.checkoutAvailable(), access.displayPrice().orElse(null));
    }
}
