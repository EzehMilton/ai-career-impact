package com.chikere.futureai.report.professional.web;

import com.chikere.futureai.report.professional.application.ProfessionalReportService;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@Profile("local-fixture")
public final class ProfessionalPaidReportPreviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalPaidReportPreviewController.class);
    private final ProfessionalReportService reportService;
    private final ProfessionalReportViewMapper viewMapper;

    public ProfessionalPaidReportPreviewController(ProfessionalReportService reportService,
            ProfessionalReportViewMapper viewMapper) {
        this.reportService = reportService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/professional-assessment/{assessmentId}/report")
    public String showPaidPreview(@PathVariable String assessmentId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            var report = reportService.assemble(id, ProfessionalReportTier.PAID);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "professional-assessment/not-found";
            }
            model.addAttribute("report", viewMapper.toView(report.orElseThrow()));
            return "professional-assessment/paid-report";
        } catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "professional-assessment/not-found";
        } catch (RuntimeException failure) {
            LOGGER.error("Paid report preview could not be loaded", failure);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }
}
