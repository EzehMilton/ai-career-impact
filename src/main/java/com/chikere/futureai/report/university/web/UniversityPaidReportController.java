package com.chikere.futureai.report.university.web;

import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.web.UniversityPurchaseViewModel;
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
public final class UniversityPaidReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityPaidReportController.class);
    private final UniversityReportPurchaseUseCase purchaseService;
    private final UniversityReportViewMapper viewMapper;

    public UniversityPaidReportController(UniversityReportPurchaseUseCase purchaseService,
            UniversityReportViewMapper viewMapper) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/university-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId, Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                model.addAttribute("assessmentId", id);
                model.addAttribute("purchase", UniversityPurchaseViewModel.from(purchaseService.accessFor(id)));
                return "university-assessment/paid-report-unavailable";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", viewMapper.toView(report.orElseThrow()));
            return "university-assessment/paid-report";
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "university-assessment/not-found";
        }
        catch (RuntimeException failure) {
            LOGGER.error("Purchased university report could not be loaded", failure);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "university-assessment/error";
        }
    }
}
