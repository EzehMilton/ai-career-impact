package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
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
 * A separate Stripe webhook endpoint for the university product.
 *
 * <p>It is exempt from browser CSRF because Stripe's signature authenticates it. Events without
 * university product metadata are ignored rather than treated as errors.</p>
 */
@RestController
@Profile("live-payment & !local-fixture")
public final class UniversityStripeWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityStripeWebhookController.class);
    private final UniversityReportPurchaseUseCase purchaseService;

    public UniversityStripeWebhookController(UniversityReportPurchaseUseCase purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/payments/stripe/university-webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) return ResponseEntity.badRequest().build();
        try {
            purchaseService.processPaymentNotification(new UniversityPaymentNotification(payload, signature));
            return ResponseEntity.ok().build();
        }
        catch (InvalidPaymentNotificationException invalid) {
            String reason = invalid.getMessage();
            LOGGER.warn("University Stripe webhook rejected reason={}",
                    reason != null && reason.matches("[A-Z_]+") ? reason : "INVALID_NOTIFICATION");
            return ResponseEntity.badRequest().build();
        }
        catch (RuntimeException retryable) {
            LOGGER.error("Verified Stripe university payment event could not be processed ({})",
                    retryable.getClass().getSimpleName());
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
        }
    }
}
