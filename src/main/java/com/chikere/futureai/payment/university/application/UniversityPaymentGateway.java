package com.chikere.futureai.payment.university.application;

import java.util.Optional;

/** Vendor-neutral payment port. Stripe SDK types never cross this boundary. */
public interface UniversityPaymentGateway {
    UniversityCheckoutSession createCheckoutSession(UniversityCheckoutRequest request);
    UniversityCheckoutSessionState checkoutSessionState(String checkoutSessionId);

    Optional<UniversityPaymentEvent> verifyPaymentNotification(UniversityPaymentNotification notification);

    UniversityPaymentProvider provider();

    String priceConfigurationKey();
}
