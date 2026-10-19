package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.configuration.FailureDescription;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Redeems the complimentary access code from the free result page. Registered under every profile:
 * only {@code app.complimentary-report.enabled} decides whether it does anything, and while that is
 * off it answers exactly like a route that does not exist. The code arrives in the POST body, is
 * checked by the application service, and is never echoed back or logged.
 */
@Controller
public final class ALevelComplimentaryAccessController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelComplimentaryAccessController.class);
    static final String INVALID_CODE_MESSAGE = "That access code is not valid.";
    static final String NOT_ELIGIBLE_MESSAGE = "An access code cannot be used for this assessment.";
    private final ALevelReportPurchaseUseCase purchaseService;

    public ALevelComplimentaryAccessController(ALevelReportPurchaseUseCase purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/a-level-assessment/{assessmentId}/complimentary-access")
    public String redeem(@PathVariable String assessmentId,
            @RequestParam(name = "accessCode", required = false) String accessCode,
            RedirectAttributes redirect, HttpServletResponse response) {
        try {
            UUID id = UUID.fromString(assessmentId);
            return switch (purchaseService.redeemComplimentaryAccess(id, accessCode)) {
                case UNLOCKED, ALREADY_ACTIVE -> "redirect:/a-level-assessment/" + id + "/report";
                case INVALID_CODE -> backToResult(id, INVALID_CODE_MESSAGE, redirect);
                case NOT_ELIGIBLE -> backToResult(id, NOT_ELIGIBLE_MESSAGE, redirect);
                case DISABLED -> notFound(response);
            };
        }
        catch (IllegalArgumentException unknown) {
            return notFound(response);
        }
        catch (RuntimeException failure) {
            LOGGER.error("A-level complimentary access could not be redeemed ({})", FailureDescription.of(failure));
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "a-level-assessment/error";
        }
    }

    private static String backToResult(UUID id, String message, RedirectAttributes redirect) {
        redirect.addFlashAttribute("complimentaryMessage", message);
        return "redirect:/a-level-assessment/" + id;
    }

    private static String notFound(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return "a-level-assessment/not-found";
    }
}
