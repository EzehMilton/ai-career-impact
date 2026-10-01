package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.PaymentUnavailableException;
import com.chikere.futureai.payment.university.application.UniversityCheckoutRequest;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

final class StripeSdkCheckoutOperations implements StripeCheckoutOperations {
    private final String secretKey;

    StripeSdkCheckoutOperations(String secretKey) {
        this.secretKey = required(secretKey, "Stripe secret key");
    }

    @Override
    public StripeCheckoutData create(UniversityCheckoutRequest request) {
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.successUrl().toString())
                .setCancelUrl(request.cancelUrl().toString())
                .setClientReferenceId(request.entitlementId().toString())
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(request.priceConfigurationKey()).setQuantity(1L).build())
                .putMetadata(UniversityStripeMetadata.PRODUCT_KEY, UniversityStripeMetadata.PRODUCT_VALUE)
                .putMetadata(UniversityStripeMetadata.ASSESSMENT_ID, request.assessmentId().toString())
                .putMetadata(UniversityStripeMetadata.ENTITLEMENT_ID, request.entitlementId().toString())
                .build();
        var options = RequestOptions.builder().setApiKey(secretKey)
                .setIdempotencyKey("university-report-" + request.entitlementId()).build();
        try {
            Session session = Session.create(params, options);
            if (session.getId() == null || session.getUrl() == null) {
                throw new PaymentUnavailableException("Stripe returned an incomplete checkout session");
            }
            return new StripeCheckoutData(session.getId(), URI.create(session.getUrl()),
                    Optional.ofNullable(session.getExpiresAt()).map(Instant::ofEpochSecond));
        }
        catch (StripeException | IllegalArgumentException failure) {
            throw new PaymentUnavailableException("Stripe checkout session creation failed", failure);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
