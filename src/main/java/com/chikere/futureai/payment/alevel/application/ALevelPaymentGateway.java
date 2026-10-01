package com.chikere.futureai.payment.alevel.application;

import java.util.Optional;

/** Vendor-neutral payment port. Stripe SDK types never cross this boundary. */
public interface ALevelPaymentGateway {
    ALevelCheckoutSession createCheckoutSession(ALevelCheckoutRequest request);

    Optional<ALevelPaymentEvent> verifyPaymentNotification(ALevelPaymentNotification notification);

    ALevelPaymentProvider provider();

    String priceConfigurationKey();
}
