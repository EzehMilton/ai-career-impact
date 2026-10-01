package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.UniversityReportPurchaseService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.util.UUID;

@Controller
public final class UniversityCheckoutController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityCheckoutController.class);
    private final UniversityReportPurchaseService purchaseService;

    public UniversityCheckoutController(UniversityReportPurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/university-assessment/{assessmentId}/checkout")
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
            LOGGER.error("University report checkout could not be started ({})",
                    failure.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Paid report checkout is currently unavailable.");
        }
    }
}
