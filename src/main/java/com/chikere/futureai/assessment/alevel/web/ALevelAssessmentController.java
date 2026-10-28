package com.chikere.futureai.assessment.alevel.web;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.web.ALevelPurchaseViewModel;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.alevel.web.ALevelReportViewMapper;
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
public final class ALevelAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelAssessmentController.class);
    private static final String FORM_VIEW = "a-level-assessment/form";

    private final ALevelAssessmentUseCase workflow;
    private final ALevelReportQuery reportService;
    private final ALevelAssessmentFormMapper formMapper;
    private final ALevelReportViewMapper reportViewMapper;
    private final ALevelReportPurchaseUseCase purchaseService;

    public ALevelAssessmentController(ALevelAssessmentUseCase workflow,
            ALevelReportQuery reportService, ALevelAssessmentFormMapper formMapper,
            ALevelReportViewMapper reportViewMapper, ALevelReportPurchaseUseCase purchaseService) {
        this.workflow = workflow;
        this.reportService = reportService;
        this.formMapper = formMapper;
        this.reportViewMapper = reportViewMapper;
        this.purchaseService = purchaseService;
    }

    @GetMapping("/a-level-assessment")
    public String showForm(Model model) {
        model.addAttribute("form", new ALevelAssessmentForm());
        return FORM_VIEW;
    }

    @GetMapping("/a-level-assessment/example")
    public String showExample() {
        return "a-level-assessment/a-level-example";
    }

    @PostMapping("/a-level-assessment")
    public String assess(@Valid @ModelAttribute("form") ALevelAssessmentForm form,
            BindingResult bindingResult, Model model, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        ALevelAssessmentState state;
        try {
            state = formMapper.toState(form);
        }
        catch (IllegalArgumentException invalidCombination) {
            // Domain rules such as "at least two distinct subjects" belong to the student, not a
            // server error: show the form again with a plain explanation.
            bindingResult.rejectValue("selectedSubjects", "selectedSubjects.invalid",
                    "Please list at least two different A-level subjects, and no more than five.");
            return FORM_VIEW;
        }
        try {
            UUID id = workflow.submit(state);
            return "redirect:/a-level-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("A-level assessment could not be completed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "a-level-assessment/error";
        }
    }

    @GetMapping("/a-level-assessment/{assessmentId}")
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
            return "a-level-assessment/not-found";
        }
        try {
            // Routed by the stored decision version: a v2 subject profile has its own page, a v1 assessment keeps v1.
            var stored = reportService.stored(id);
            if (stored.filter(value -> value.result().usesSubjectProfile()).isPresent()) {
                return subjectProfile(stored.get(), checkout, model);
            }
            var report = reportService.assemble(id, ALevelReportTier.FREE);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "a-level-assessment/not-found";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", reportViewMapper.toView(report.orElseThrow()));
            var purchase = ALevelPurchaseViewModel.from(purchaseService.accessFor(id));
            // The Stripe return only chooses which page to show. Access is still decided by the
            // entitlement that the verified webhook activated, and the report checks it again.
            boolean returnedFromCheckout = "returned".equals(checkout);
            if (returnedFromCheckout && purchase.active()) {
                return "redirect:/a-level-assessment/" + id + "/report";
            }
            model.addAttribute("purchase", purchase);
            model.addAttribute("paymentPanel", paymentPanel(checkout));
            return "a-level-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Saved A-level assessment could not be loaded for id {}", id, exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "a-level-assessment/error";
        }
    }

    /** The deterministic v2 free result, with the a-level-report-v2 purchase panel. */
    private String subjectProfile(StoredALevelAssessment assessment, String checkout, Model model) {
        var purchase = ALevelPurchaseViewModel.from(purchaseService.accessFor(assessment.id()));
        if ("returned".equals(checkout) && purchase.active()) {
            return "redirect:/a-level-assessment/" + assessment.id() + "/report";
        }
        model.addAttribute("purchase", purchase);
        model.addAttribute("paymentPanel", paymentPanel(checkout));
        model.addAttribute("assessmentId", assessment.id());
        model.addAttribute("subjects", assessment.state().selectedSubjects());
        model.addAttribute("developmentFixture", assessment.result().source() == AssessmentSource.LOCAL_FIXTURE);
        model.addAttribute("profile", reportService.subjectProfile(assessment.id()).orElseThrow());
        return "a-level-assessment/result-v2";
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
