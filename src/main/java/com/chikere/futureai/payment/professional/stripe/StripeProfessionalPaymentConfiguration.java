package com.chikere.futureai.payment.professional.stripe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev"})
public class StripeProfessionalPaymentConfiguration {
    @Bean
    StripeProfessionalPaymentGateway stripeProfessionalPaymentGateway(
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.professional.report.stripe-price-id:}") String priceId) {
        return new StripeProfessionalPaymentGateway(new StripeSdkCheckoutOperations(secretKey),
                new StripeSdkWebhookEventVerifier(webhookSecret), priceId);
    }
}
