package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeCompletedSessionValidationTest {
    private static final UUID ENTITLEMENT = UUID.randomUUID();
    private static final UUID ASSESSMENT = UUID.randomUUID();

    @Test
    void acceptsMatchingPaidSessionAndRejectsWrongPriceOrAmount() {
        var event = event();
        var session = session();
        var items = items("price_expected", 499L);

        StripeSdkCheckoutOperations.validateFetchedSession(event, "price_expected", session, items);
        assertThatThrownBy(() -> StripeSdkCheckoutOperations.validateFetchedSession(
                event, "price_expected", session, items("price_other", 499L)))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("STRIPE_PRICE_OR_LINE_AMOUNT_MISMATCH");
        assertThatThrownBy(() -> StripeSdkCheckoutOperations.validateFetchedSession(
                event, "price_expected", session, items("price_expected", 498L)))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        session.setCurrency("usd");
        assertThatThrownBy(() -> StripeSdkCheckoutOperations.validateFetchedSession(
                event, "price_expected", session, items))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("STRIPE_AMOUNT_OR_CURRENCY_MISMATCH");
    }

    @Test
    void rejectsWrongReferenceOrExtraLineItems() {
        var session = session();
        session.setClientReferenceId(UUID.randomUUID().toString());
        assertThatThrownBy(() -> StripeSdkCheckoutOperations.validateFetchedSession(
                event(), "price_expected", session, items("price_expected", 499L)))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("STRIPE_SESSION_MISMATCH");

        session.setClientReferenceId(ENTITLEMENT.toString());
        var extra = items("price_expected", 499L);
        extra.setHasMore(true);
        assertThatThrownBy(() -> StripeSdkCheckoutOperations.validateFetchedSession(
                event(), "price_expected", session, extra))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("STRIPE_LINE_ITEMS_INVALID");
    }

    private static StripeVerifiedEvent event() {
        return new StripeVerifiedEvent("evt_test", "cs_test", ENTITLEMENT, ASSESSMENT,
                Optional.of("pi_test"), Instant.EPOCH, 499, "gbp");
    }

    private static Session session() {
        var session = new Session();
        session.setId("cs_test");
        session.setPaymentStatus("paid");
        session.setMode("payment");
        session.setClientReferenceId(ENTITLEMENT.toString());
        session.setMetadata(Map.of("entitlement_id", ENTITLEMENT.toString(),
                "assessment_id", ASSESSMENT.toString()));
        session.setAmountTotal(499L);
        session.setCurrency("gbp");
        return session;
    }

    private static LineItemCollection items(String priceId, long amount) {
        var price = new Price();
        price.setId(priceId);
        price.setCurrency("gbp");
        var line = new LineItem();
        line.setPrice(price);
        line.setQuantity(1L);
        line.setAmountTotal(amount);
        line.setCurrency("gbp");
        var items = new LineItemCollection();
        items.setData(List.of(line));
        items.setHasMore(false);
        return items;
    }
}
