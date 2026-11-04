package com.chikere.futureai.report.university.web;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisFailedException;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.web.UniversityPurchaseViewModel;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportUseCase;
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
 * The paid University report, only for an ACTIVE entitlement. A historical purchase renders its frozen v1 snapshot.
 * A university-report-v2 purchase renders its stored detailed report on the page of that document's own version (a
 * stored v2 document on the v2 page, a v3 document on the v3 page), or its provisional report, or the step that
 * prepares it: preparation is an explicit POST, so viewing a page never generates anything.
 */
@Controller
public final class UniversityPaidReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityPaidReportController.class);
    private static final Set<String> PREPARATION_STATUSES = Set.of("selection-required", "invalid-selection",
            "unavailable", "failed");
    private final UniversityReportPurchaseUseCase purchaseService;
    private final UniversityReportViewMapper viewMapper;
    private final ObjectProvider<UniversityDetailedReportUseCase> detailedReports;

    /** The detailed report resolves optionally, so a sliced web test without it still loads this controller. */
    public UniversityPaidReportController(UniversityReportPurchaseUseCase purchaseService,
            UniversityReportViewMapper viewMapper, ObjectProvider<UniversityDetailedReportUseCase> detailedReports) {
        this.purchaseService = purchaseService;
        this.viewMapper = viewMapper;
        this.detailedReports = detailedReports;
    }

    @GetMapping("/university-assessment/{assessmentId}/report")
    public String showPurchasedReport(@PathVariable String assessmentId,
            @RequestParam(name = "preparation", required = false) String preparationStatus, Model model,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        try {
            UUID id = UUID.fromString(assessmentId);
            if (isCourseProfilePurchase(id)) {
                return showDetailedReport(id, preparationStatus, model, response);
            }
            var report = purchaseService.findPurchasedReport(id);
            if (report.isEmpty()) {
                return unavailable(id, model, response);
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

    /**
     * Prepares the v2 report once. {@code selectionForm} marks a submission of the selection screen, so submitting it
     * with nothing chosen is an invalid selection rather than no selection. The service validates the selection
     * before any provider call; nothing here trusts the browser.
     */
    @PostMapping("/university-assessment/{assessmentId}/report/prepare")
    public String prepare(@PathVariable String assessmentId,
            @RequestParam(name = "module", required = false) List<Integer> module,
            @RequestParam(name = "selectionForm", required = false) String selectionForm, Model model,
            HttpServletResponse response) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException malformed) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "university-assessment/not-found";
        }
        UniversityDetailedReportUseCase detailed = detailedReports.getIfAvailable();
        if (detailed == null) {
            return redirect(id, "unavailable");
        }
        Optional<List<Integer>> selection = selectionForm == null && (module == null || module.isEmpty())
                ? Optional.empty() : Optional.of(module == null ? List.of() : List.copyOf(module));
        try {
            boolean retry = detailed.provisional(id).isPresent();
            var outcome = detailed.prepare(id, selection);
            // A retry that is still provisional says so; a first provisional result is explained by the page itself.
            return redirect(id, outcome.provisional() && retry ? "failed" : null);
        }
        catch (UniversityModuleAnalysisUnavailableException unavailable) {
            return switch (unavailable.reason()) {
                case NOT_ENTITLED, ASSESSMENT_NOT_FOUND -> unavailable(id, model, response);
                case MODULE_SELECTION_REQUIRED -> redirect(id, "selection-required");
                case INVALID_SELECTION -> redirect(id, "invalid-selection");
                default -> redirect(id, "unavailable");
            };
        }
        catch (UniversityModuleAnalysisFailedException failed) {
            LOGGER.warn("Detailed university report could not be prepared assessmentId={} category={}", id,
                    failed.category());
            return redirect(id, "failed");
        }
        catch (RuntimeException failure) {
            LOGGER.error("Detailed university report could not be prepared assessmentId={}", id, failure);
            return redirect(id, "failed");
        }
    }

    private boolean isCourseProfilePurchase(UUID id) {
        return detailedReports.getIfAvailable() != null
                && purchaseService.activePurchaseVersion(id).filter(UniversityReportVersions.V2::equals).isPresent();
    }

    private String showDetailedReport(UUID id, String preparationStatus, Model model, HttpServletResponse response) {
        UniversityDetailedReportUseCase detailed = detailedReports.getObject();
        model.addAttribute("assessmentId", id);
        var stored = detailed.find(id);
        if (stored.isPresent()) {
            model.addAttribute("report", stored.get().report());
            model.addAttribute("provisional", false);
            return UniversityReportVersions.detailedTemplate(stored.get().reportVersion());
        }
        var provisional = detailed.provisional(id);
        if (provisional.isPresent()) {
            model.addAttribute("report", provisional.get());
            model.addAttribute("provisional", true);
            model.addAttribute("preparationStatus", "failed".equals(preparationStatus) ? preparationStatus : null);
            return UniversityReportVersions.detailedTemplate(provisional.get().reportVersion());
        }
        var preparation = detailed.preparation(id);
        if (preparation.isEmpty()) {
            return unavailable(id, model, response);
        }
        model.addAttribute("preparation", preparation.get());
        model.addAttribute("preparationStatus",
                preparationStatus != null && PREPARATION_STATUSES.contains(preparationStatus)
                        ? preparationStatus : null);
        return "university-assessment/paid-report-v2-prepare";
    }

    private String unavailable(UUID id, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("assessmentId", id);
        model.addAttribute("purchase", UniversityPurchaseViewModel.from(purchaseService.accessFor(id)));
        return "university-assessment/paid-report-unavailable";
    }

    private static String redirect(UUID id, String preparationStatus) {
        return "redirect:/university-assessment/" + id + "/report"
                + (preparationStatus == null ? "" : "?preparation=" + preparationStatus);
    }
}
