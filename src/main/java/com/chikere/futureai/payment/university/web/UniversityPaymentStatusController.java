package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only view of the saved entitlement, polled by the result page after a return from Stripe.
 * It reports what the verified webhook has already recorded and never asks Stripe or changes state:
 * activation stays with the webhook. Only the status name leaves the server.
 */
@RestController
public final class UniversityPaymentStatusController {
    private final UniversityReportPurchaseUseCase purchaseService;

    public UniversityPaymentStatusController(UniversityReportPurchaseUseCase purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping(value = "/university-assessment/{assessmentId}/payment-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentStatus> status(@PathVariable String assessmentId) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException malformed) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore().cachePrivate()).build();
        }
        return purchaseService.accessFor(id).status()
                .map(status -> ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                        .body(new PaymentStatus(status.name())))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore().cachePrivate()).build());
    }

    public record PaymentStatus(String status) { }
}
