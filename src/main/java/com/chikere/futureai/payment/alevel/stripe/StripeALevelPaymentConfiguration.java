package com.chikere.futureai.payment.alevel.stripe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev"})
@ConditionalOnProperty(prefix = "app.stripe", name = "enabled", havingValue = "true")
public class StripeALevelPaymentConfiguration {

    @Bean
    StripeALevelPaymentGateway stripeALevelPaymentGateway(
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.a-level-webhook-secret:}") String webhookSecret,
            @Value("${app.a-level.report.stripe-price-id:}") String priceId) {
        return new StripeALevelPaymentGateway(new StripeSdkCheckoutOperations(secretKey),
                new StripeSdkWebhookEventVerifier(webhookSecret), priceId);
    }
}
