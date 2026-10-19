package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.UniversityCheckoutRequest;
import com.chikere.futureai.payment.university.application.UniversityCheckoutSessionState;

interface StripeCheckoutOperations {
    StripeCheckoutData create(UniversityCheckoutRequest request);
    UniversityCheckoutSessionState state(String checkoutSessionId);
    void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId);
}
