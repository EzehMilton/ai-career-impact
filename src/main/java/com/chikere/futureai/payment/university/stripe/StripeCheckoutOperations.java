package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.UniversityCheckoutRequest;

interface StripeCheckoutOperations {
    StripeCheckoutData create(UniversityCheckoutRequest request);
    void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId);
}
