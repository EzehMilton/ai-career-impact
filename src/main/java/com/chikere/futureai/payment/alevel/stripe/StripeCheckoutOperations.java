package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutRequest;
import com.chikere.futureai.payment.alevel.application.ALevelCheckoutSessionState;

interface StripeCheckoutOperations {
    StripeCheckoutData create(ALevelCheckoutRequest request);
    ALevelCheckoutSessionState state(String checkoutSessionId);
    void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId);
}
