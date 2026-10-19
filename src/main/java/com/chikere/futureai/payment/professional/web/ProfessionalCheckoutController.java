package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.CheckoutAwaitingConfirmationException;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import jakarta.servlet.http.HttpServletResponse;
import com.chikere.futureai.configuration.FailureDescription;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.util.UUID;

@Controller
public final class ProfessionalCheckoutController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalCheckoutController.class);
    private final ProfessionalReportPurchaseUseCase purchaseService;
    private final JourneyMetrics metrics;

    /** Metrics resolve optionally so a sliced web test without a registry still loads this controller. */
    public ProfessionalCheckoutController(ProfessionalReportPurchaseUseCase purchaseService,
            ObjectProvider<JourneyMetrics> metrics) {
        this.purchaseService = purchaseService;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
    }

    @PostMapping("/professional-assessment/{assessmentId}/checkout")
    public void startCheckout(@PathVariable String assessmentId, HttpServletResponse response) throws IOException {
        try {
            var session = purchaseService.startCheckout(UUID.fromString(assessmentId));
            response.sendRedirect(session.redirectUrl().toString());
        }
        catch (IllegalArgumentException unknown) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (CheckoutAwaitingConfirmationException alreadyPaid) {
            // Paid at Stripe but not yet confirmed by the webhook: wait for it, never pay again.
            response.sendRedirect("/professional-assessment/" + UUID.fromString(assessmentId) + "?checkout=returned");
        }
        catch (RuntimeException failure) {
            metrics.checkoutFailed("professional");
            LOGGER.error("Professional report checkout could not be started ({})",
                    FailureDescription.of(failure));
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Paid report checkout is currently unavailable.");
        }
    }
}
