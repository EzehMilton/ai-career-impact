package com.chikere.futureai.report.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.web.ALevelPurchaseViewModel;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportUnavailableException;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportUseCase;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * The paid A-level report, only for an ACTIVE entitlement. A historical purchase renders its frozen v1 snapshot. An
 * a-level-report-v2 purchase renders its stored detailed report; otherwise the step that prepares it, which is an
 * explicit POST, so viewing a page never generates anything. A preparation whose paid narrative was retryable stores
 * nothing and returns here with {@code ?preparation=provisional}, which shows the deterministic report and a retry.
 */
@Controller
public final class ALevelPaidReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelPaidReportController.class);
    private final ALevelReportPurchaseUseCase purchaseService;
    private final ALevelReportViewMapper viewMapper;
    private final ObjectProvider<ALevelDetailedReportUseCase> detailedReports;

    /** The detailed report resolves optionally, so a sliced web test without it still loads this controller. */
    public ALevelPaidReportController(ALevelReportPurchaseUseCase purchaseService,
            ALevelReportViewMapper viewMapper, ObjectProvider<ALevelDetailedReportUseCase> detailedReports) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
        this.detailedReports = detailedReports;
    }

    @GetMapping("/a-level-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId,
            @RequestParam(name = "preparation", required = false) String preparationStatus, Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            if (isSubjectProfilePurchase(id)) {
                return showDetailedReport(id, preparationStatus, model, response);
            }
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                return unavailable(id, model, response);
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

    /** Prepares the v2 report once, on the customer's request. It never calls Jev. */
    @PostMapping("/a-level-assessment/{assessmentId}/report/prepare")
    public String prepare(@PathVariable String assessmentId, Model model, HttpServletResponse response) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "a-level-assessment/not-found";
        }
        ALevelDetailedReportUseCase detailed = detailedReports.getIfAvailable();
        if (detailed == null) {
            return redirect(id, "failed");
        }
        try {
            var outcome = detailed.prepare(id);
            return redirect(id, outcome.provisional() ? "provisional" : null);
        }
        catch (ALevelDetailedReportUnavailableException unavailable) {
            return unavailable(id, model, response);
        }
        catch (RuntimeException failure) {
            LOGGER.error("Detailed A-level report could not be prepared assessmentId={}", id, failure);
            return redirect(id, "failed");
        }
    }

    private boolean isSubjectProfilePurchase(UUID id) {
        return detailedReports.getIfAvailable() != null
                && purchaseService.activePurchaseVersion(id).filter(ALevelReportVersions.V2::equals).isPresent();
    }

    private String showDetailedReport(UUID id, String preparationStatus, Model model, HttpServletResponse response) {
        ALevelDetailedReportUseCase detailed = detailedReports.getObject();
        model.addAttribute("assessmentId", id);
        var stored = detailed.find(id);
        if (stored.isPresent()) {
            model.addAttribute("report", stored.get().report());
            model.addAttribute("provisional", false);
            return "a-level-assessment/paid-report-v2";
        }
        if ("provisional".equals(preparationStatus)) {
            var provisional = detailed.provisional(id);
            if (provisional.isPresent()) {
                model.addAttribute("report", provisional.get());
                model.addAttribute("provisional", true);
                return "a-level-assessment/paid-report-v2";
            }
        }
        if (detailed.preparation(id).isEmpty()) {
            return unavailable(id, model, response);
        }
        model.addAttribute("preparationStatus", "failed".equals(preparationStatus) ? preparationStatus : null);
        return "a-level-assessment/paid-report-v2-prepare";
    }

    private String unavailable(UUID id, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("assessmentId", id);
        model.addAttribute("purchase", ALevelPurchaseViewModel.from(purchaseService.accessFor(id)));
        return "a-level-assessment/paid-report-unavailable";
    }

    private static String redirect(UUID id, String preparationStatus) {
        return "redirect:/a-level-assessment/" + id + "/report"
                + (preparationStatus == null ? "" : "?preparation=" + preparationStatus);
    }
}
