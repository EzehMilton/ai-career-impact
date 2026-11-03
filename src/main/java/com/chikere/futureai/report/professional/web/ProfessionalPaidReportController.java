package com.chikere.futureai.report.professional.web;

import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.professional.web.ProfessionalPurchaseViewModel;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportUseCase;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportVersions;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportViewMapper;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailedException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The paid Professional report. A stored detailed report (v3 or v4, each with its own page) is shown when it exists. Otherwise the frozen
 * role-level report (professional-report-v2) is shown with the step that prepares the detailed report, which is an
 * explicit POST: viewing a page never generates anything. Access always requires an ACTIVE entitlement.
 */
@Controller
public final class ProfessionalPaidReportController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalPaidReportController.class);
    private static final Set<String> PREPARATION_STATUSES = Set.of("selection-required", "invalid-selection",
            "unavailable", "failed");

    private final ProfessionalReportPurchaseUseCase purchaseService;
    private final ProfessionalReportViewMapper viewMapper;
    private final ObjectProvider<ProfessionalDetailedReportUseCase> detailedReports;
    private final ObjectProvider<ProfessionalDetailedReportViewMapper> detailedViews;

    /** The detailed report resolves optionally, so a sliced web test without it still loads this controller. */
    public ProfessionalPaidReportController(ProfessionalReportPurchaseUseCase purchaseService,
            ProfessionalReportViewMapper viewMapper, ObjectProvider<ProfessionalDetailedReportUseCase> detailedReports,
            ObjectProvider<ProfessionalDetailedReportViewMapper> detailedViews) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
        this.detailedReports = detailedReports;
        this.detailedViews = detailedViews;
    }

    @GetMapping("/professional-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId,
            @RequestParam(name = "preparation", required = false) String preparationStatus, Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                return unavailable(id, model, response);
            }
            model.addAttribute("assessmentId", id);
            ProfessionalDetailedReportUseCase detailed = detailedReports.getIfAvailable();
            if (detailed != null) {
                var stored = detailed.find(id);
                if (stored.isPresent()) {
                    // A stored report keeps the page of the version it was assembled as; v3 is never re-rendered as v4.
                    model.addAttribute("detailed", detailedViews.getObject().toView(stored.get()));
                    return ProfessionalDetailedReportVersions.template(stored.get().reportVersion());
                }
                // Prepared, but the paid narrative is still retryable: the complete deterministic report, not stored.
                var provisional = detailed.provisional(id);
                if (provisional.isPresent()) {
                    model.addAttribute("detailed", detailedViews.getObject().toProvisionalView(provisional.get()));
                    model.addAttribute("preparationStatus",
                            "failed".equals(preparationStatus) ? preparationStatus : null);
                    return ProfessionalDetailedReportVersions.template(provisional.get().reportVersion());
                }
                model.addAttribute("preparation", detailed.preparation(id).orElse(null));
                model.addAttribute("preparationStatus",
                        preparationStatus != null && PREPARATION_STATUSES.contains(preparationStatus)
                                ? preparationStatus : null);
            }
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

    /**
     * Prepares the detailed report once. {@code selectionForm} marks a submission of the selection screen, so
     * submitting it with nothing chosen is an invalid selection rather than no selection. The service validates
     * the selection; nothing here trusts the browser.
     */
    @PostMapping("/professional-assessment/{assessmentId}/report/prepare")
    public String prepare(@PathVariable String assessmentId,
            @RequestParam(name = "responsibility", required = false) List<Integer> responsibility,
            @RequestParam(name = "selectionForm", required = false) String selectionForm, Model model,
            HttpServletResponse response) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "professional-assessment/not-found";
        }
        ProfessionalDetailedReportUseCase detailed = detailedReports.getIfAvailable();
        if (detailed == null) {
            return redirect(id, "unavailable");
        }
        Optional<List<Integer>> selection = selectionForm == null && (responsibility == null || responsibility.isEmpty())
                ? Optional.empty() : Optional.of(responsibility == null ? List.of() : List.copyOf(responsibility));
        try {
            boolean retry = detailed.provisional(id).isPresent();
            var outcome = detailed.prepare(id, selection);
            // A retry that is still provisional says so; a first provisional result is explained by the page itself.
            return redirect(id, outcome.provisional() && retry ? "failed" : null);
        }
        catch (ResponsibilityAnalysisUnavailableException unavailable) {
            return switch (unavailable.reason()) {
                case NOT_ENTITLED, ASSESSMENT_NOT_FOUND -> unavailable(id, model, response);
                case RESPONSIBILITY_SELECTION_REQUIRED -> redirect(id, "selection-required");
                case INVALID_SELECTION -> redirect(id, "invalid-selection");
                default -> redirect(id, "unavailable");
            };
        }
        catch (ResponsibilityAnalysisFailedException failed) {
            LOGGER.warn("Detailed professional report could not be prepared assessmentId={} stage={}", id, failed.stage());
            return redirect(id, "failed");
        }
        catch (RuntimeException failure) {
            LOGGER.error("Detailed professional report could not be prepared assessmentId={}", id, failure);
            return redirect(id, "failed");
        }
    }

    private String unavailable(UUID id, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("assessmentId", id);
        model.addAttribute("purchase", ProfessionalPurchaseViewModel.from(purchaseService.accessFor(id)));
        return "professional-assessment/paid-report-unavailable";
    }

    private static String redirect(UUID id, String preparationStatus) {
        return "redirect:/professional-assessment/" + id + "/report"
                + (preparationStatus == null ? "" : "?preparation=" + preparationStatus);
    }
}
