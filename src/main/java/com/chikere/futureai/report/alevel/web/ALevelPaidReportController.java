package com.chikere.futureai.report.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.web.ALevelPurchaseViewModel;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Renders only the frozen purchased snapshot, and only for an ACTIVE entitlement. */
@Controller
public final class ALevelPaidReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelPaidReportController.class);
    private final ALevelReportPurchaseUseCase purchaseService;
    private final ALevelReportViewMapper viewMapper;

    public ALevelPaidReportController(ALevelReportPurchaseUseCase purchaseService,
            ALevelReportViewMapper viewMapper) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/a-level-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId, Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                model.addAttribute("assessmentId", id);
                model.addAttribute("purchase", ALevelPurchaseViewModel.from(purchaseService.accessFor(id)));
                return "a-level-assessment/paid-report-unavailable";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", viewMapper.toView(report.orElseThrow()));
            return "a-level-assessment/paid-report";
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "a-level-assessment/not-found";
        }
        catch (RuntimeException failure) {
            LOGGER.error("Purchased A-level report could not be loaded", failure);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "a-level-assessment/error";
        }
    }
}
