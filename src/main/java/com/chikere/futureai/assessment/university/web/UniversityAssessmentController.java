package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentUseCase;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.web.UniversityPurchaseViewModel;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.report.university.web.UniversityReportViewMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Thin controller: validation, one workflow call, Post/Redirect/Get, read-only historical GET. */
@Controller
public final class UniversityAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityAssessmentController.class);
    private static final String FORM_VIEW = "university-assessment/form";

    private final UniversityAssessmentUseCase workflow;
    private final UniversityReportQuery reportService;
    private final UniversityAssessmentFormMapper formMapper;
    private final UniversityReportViewMapper reportViewMapper;
    private final UniversityReportPurchaseUseCase purchaseService;

    public UniversityAssessmentController(
            UniversityAssessmentUseCase workflow,
            UniversityReportQuery reportService,
            UniversityAssessmentFormMapper formMapper,
            UniversityReportViewMapper reportViewMapper,
            UniversityReportPurchaseUseCase purchaseService) {
        this.workflow = workflow;
        this.reportService = reportService;
        this.formMapper = formMapper;
        this.reportViewMapper = reportViewMapper;
        this.purchaseService = purchaseService;
    }

    @GetMapping("/university-assessment")
    public String showForm(Model model) {
        model.addAttribute("form", new UniversityAssessmentForm());
        return FORM_VIEW;
    }

    @PostMapping("/university-assessment")
    public String assess(
            @Valid @ModelAttribute("form") UniversityAssessmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            UniversityAssessmentState state = formMapper.toState(form);
            UUID id = workflow.submit(state);
            return "redirect:/university-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("University assessment could not be completed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "university-assessment/error";
        }
    }

    @GetMapping("/university-assessment/{assessmentId}")
    public String showResult(@PathVariable String assessmentId,
            @RequestParam(name = "checkout", required = false) String checkout,
            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "university-assessment/not-found";
        }
        try {
            var report = reportService.assemble(id, UniversityReportTier.FREE);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "university-assessment/not-found";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", reportViewMapper.toView(report.orElseThrow()));
            var purchase = UniversityPurchaseViewModel.from(purchaseService.accessFor(id));
            // The Stripe return only chooses which page to show. Access is still decided by the
            // entitlement that the verified webhook activated, and the report checks it again.
            boolean returnedFromCheckout = "returned".equals(checkout);
            if (returnedFromCheckout && purchase.active()) {
                return "redirect:/university-assessment/" + id + "/report";
            }
            model.addAttribute("purchase", purchase);
            model.addAttribute("paymentPanel", paymentPanel(checkout));
            return "university-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Saved university assessment could not be loaded for id {}", id, exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "university-assessment/error";
        }
    }

    /**
     * Which pending-payment panel to show. Only presentation: a PENDING purchase stays PENDING until
     * the verified webhook activates it, whatever the browser claims about where it came from.
     */
    private static String paymentPanel(String checkout) {
        if ("returned".equals(checkout)) return "confirming";
        if ("cancelled".equals(checkout)) return "cancelled";
        return "pending";
    }
}
