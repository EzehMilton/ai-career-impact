package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("live-payment & !local-fixture")
public final class StripeWebhookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookController.class);
    private final ProfessionalReportPurchaseService purchaseService;

    public StripeWebhookController(ProfessionalReportPurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/payments/stripe/webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) return ResponseEntity.badRequest().build();
        try {
            purchaseService.processPaymentNotification(new ProfessionalPaymentNotification(payload, signature));
            return ResponseEntity.ok().build();
        }
        catch (InvalidPaymentNotificationException invalid) {
            String reason = invalid.getMessage();
            LOGGER.warn("Professional Stripe webhook rejected reason={}",
                    reason != null && reason.matches("[A-Z_]+") ? reason : "INVALID_NOTIFICATION");
            return ResponseEntity.badRequest().build();
        }
        catch (RuntimeException retryable) {
            LOGGER.error("Verified Stripe payment event could not be processed ({})",
                    retryable.getClass().getSimpleName());
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).build();
        }
    }
}
