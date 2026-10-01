package com.chikere.futureai.payment.professional.application;

import java.util.Optional;

public interface ProfessionalPaymentGateway {
    ProfessionalCheckoutSession createCheckoutSession(ProfessionalCheckoutRequest request);
    Optional<ProfessionalPaymentEvent> verifyPaymentNotification(ProfessionalPaymentNotification notification);
    ProfessionalPaymentProvider provider();
    String priceConfigurationKey();
}
