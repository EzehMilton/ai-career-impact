package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutRequest;

interface StripeCheckoutOperations {
    StripeCheckoutData create(ProfessionalCheckoutRequest request);
}
