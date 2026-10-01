package com.chikere.futureai.payment.university.stripe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("live-payment & !local-fixture")
public class StripeUniversityPaymentConfiguration {

    @Bean
    StripeUniversityPaymentGateway stripeUniversityPaymentGateway(
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.university-webhook-secret:}") String webhookSecret,
            @Value("${app.university.report.stripe-price-id:}") String priceId) {
        return new StripeUniversityPaymentGateway(new StripeSdkCheckoutOperations(secretKey),
                new StripeSdkWebhookEventVerifier(webhookSecret), priceId);
    }
}
