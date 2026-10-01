package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.payment.alevel.application.InvalidPaymentNotificationException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * A separate Stripe webhook endpoint for the A-level product.
 *
 * <p>It is exempt from browser CSRF because Stripe's signature authenticates it. Events without
 * A-level product metadata are ignored rather than treated as errors.</p>
 */
@RestController
@Profile("live-payment & !local-fixture")
public final class ALevelStripeWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelStripeWebhookController.class);
    private final ALevelReportPurchaseService purchaseService;

    public ALevelStripeWebhookController(ALevelReportPurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/payments/stripe/a-level-webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) return ResponseEntity.badRequest().build();
        try {
            purchaseService.processPaymentNotification(new ALevelPaymentNotification(payload, signature));
            return ResponseEntity.ok().build();
        }
        catch (InvalidPaymentNotificationException invalid) {
            return ResponseEntity.badRequest().build();
        }
        catch (RuntimeException retryable) {
            LOGGER.error("Verified Stripe A-level payment event could not be processed ({})",
                    retryable.getClass().getSimpleName());
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
        }
    }
}
