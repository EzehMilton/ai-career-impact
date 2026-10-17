package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
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
public final class ALevelCheckoutController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelCheckoutController.class);
    private final ALevelReportPurchaseUseCase purchaseService;
    private final JourneyMetrics metrics;

    /** Metrics resolve optionally so a sliced web test without a registry still loads this controller. */
    public ALevelCheckoutController(ALevelReportPurchaseUseCase purchaseService,
            ObjectProvider<JourneyMetrics> metrics) {
        this.purchaseService = purchaseService;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
    }

    @PostMapping("/a-level-assessment/{assessmentId}/checkout")
    public void startCheckout(@PathVariable String assessmentId, HttpServletResponse response)
            throws IOException {
        try {
            var session = purchaseService.startCheckout(UUID.fromString(assessmentId));
            response.sendRedirect(session.redirectUrl().toString());
        }
        catch (IllegalArgumentException unknown) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (RuntimeException failure) {
            metrics.checkoutFailed("a-level");
            LOGGER.error("A-level report checkout could not be started ({})",
                    FailureDescription.of(failure));
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Paid report checkout is currently unavailable.");
        }
    }
}
