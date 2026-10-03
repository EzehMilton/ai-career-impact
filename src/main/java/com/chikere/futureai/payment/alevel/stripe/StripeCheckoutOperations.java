package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutRequest;

interface StripeCheckoutOperations {
    StripeCheckoutData create(ALevelCheckoutRequest request);
    void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId);
}
