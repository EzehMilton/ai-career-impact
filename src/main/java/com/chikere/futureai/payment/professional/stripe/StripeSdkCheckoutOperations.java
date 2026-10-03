package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.PaymentUnavailableException;
import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutRequest;
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
    public StripeCheckoutData create(ProfessionalCheckoutRequest request) {
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.successUrl().toString())
                .setCancelUrl(request.cancelUrl().toString())
                .setClientReferenceId(request.entitlementId().toString())
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(request.priceConfigurationKey()).setQuantity(1L).build())
                .putMetadata("assessment_id", request.assessmentId().toString())
                .putMetadata("entitlement_id", request.entitlementId().toString())
                .build();
        var options = RequestOptions.builder().setApiKey(secretKey)
                .setIdempotencyKey("professional-report-" + request.entitlementId()).build();
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

    @Override
    public void validateCompletedSession(StripeVerifiedEvent event, String expectedPriceId) {
        var options = RequestOptions.builder().setApiKey(secretKey).build();
        final Session session;
        final com.stripe.model.LineItemCollection lineItems;
        try {
            session = Session.retrieve(event.sessionId(), options);
            lineItems = session.listLineItems(java.util.Map.of(), options);
        }
        catch (StripeException unavailable) {
            // Stripe outages must remain retryable; a valid signed event must not be discarded.
            throw new PaymentUnavailableException("Stripe checkout validation is temporarily unavailable", unavailable);
        }
        validateFetchedSession(event, expectedPriceId, session, lineItems);
    }

    static void validateFetchedSession(StripeVerifiedEvent event, String expectedPriceId,
            Session session, com.stripe.model.LineItemCollection lineItems) {
        if (!event.sessionId().equals(session.getId())
                || !"paid".equals(session.getPaymentStatus())
                || !"payment".equals(session.getMode())
                || !event.entitlementId().toString().equals(session.getClientReferenceId())
                || session.getMetadata() == null
                || !event.entitlementId().toString().equals(session.getMetadata().get("entitlement_id"))
                || !event.assessmentId().toString().equals(session.getMetadata().get("assessment_id"))) {
            throw new InvalidPaymentNotificationException("STRIPE_SESSION_MISMATCH");
        }
        if (!java.util.Objects.equals(event.amountTotal(), session.getAmountTotal())
                || !event.currency().equalsIgnoreCase(session.getCurrency())) {
            throw new InvalidPaymentNotificationException("STRIPE_AMOUNT_OR_CURRENCY_MISMATCH");
        }
        if (lineItems == null || lineItems.getData() == null || lineItems.getData().size() != 1
                || Boolean.TRUE.equals(lineItems.getHasMore())) {
            throw new InvalidPaymentNotificationException("STRIPE_LINE_ITEMS_INVALID");
        }
        var line = lineItems.getData().getFirst();
        if (line.getPrice() == null || !expectedPriceId.equals(line.getPrice().getId())
                || !Long.valueOf(1).equals(line.getQuantity())
                || line.getAmountTotal() == null || line.getAmountTotal() <= 0
                || !line.getAmountTotal().equals(session.getAmountTotal())
                || !session.getCurrency().equalsIgnoreCase(line.getCurrency())
                || !session.getCurrency().equalsIgnoreCase(line.getPrice().getCurrency())) {
            throw new InvalidPaymentNotificationException("STRIPE_PRICE_OR_LINE_AMOUNT_MISMATCH");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
