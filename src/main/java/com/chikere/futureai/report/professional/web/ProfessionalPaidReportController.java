package com.chikere.futureai.report.professional.web;

import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.professional.web.ProfessionalPurchaseViewModel;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
public final class ProfessionalPaidReportController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalPaidReportController.class);
    private final ProfessionalReportPurchaseUseCase purchaseService;
    private final ProfessionalReportViewMapper viewMapper;

    public ProfessionalPaidReportController(ProfessionalReportPurchaseUseCase purchaseService,
            ProfessionalReportViewMapper viewMapper) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/professional-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                model.addAttribute("assessmentId", id);
                model.addAttribute("purchase", ProfessionalPurchaseViewModel.from(purchaseService.accessFor(id)));
                return "professional-assessment/paid-report-unavailable";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", viewMapper.toView(report.orElseThrow()));
            return "professional-assessment/paid-report";
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "professional-assessment/not-found";
        }
        catch (RuntimeException failure) {
            LOGGER.error("Purchased professional report could not be loaded", failure);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }
}
