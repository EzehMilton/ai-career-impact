package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutRequest;
import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutSessionState;

interface StripeCheckoutOperations {
    StripeCheckoutData create(ProfessionalCheckoutRequest request);
    ProfessionalCheckoutSessionState state(String checkoutSessionId);
    void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId);
}
