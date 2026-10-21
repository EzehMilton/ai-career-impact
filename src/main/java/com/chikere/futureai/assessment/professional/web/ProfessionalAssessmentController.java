package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.professional.web.ProfessionalPurchaseViewModel;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
public final class ProfessionalAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentController.class);
    private static final String FORM_VIEW = "professional-assessment/form";

    private final ProfessionalAssessmentUseCase workflow;
    private final ProfessionalReportQuery reportService;
    private final ProfessionalAssessmentFormMapper formMapper;
    private final ProfessionalReportViewMapper reportViewMapper;
    private final ProfessionalReportPurchaseUseCase purchaseService;

    public ProfessionalAssessmentController(
            ProfessionalAssessmentUseCase workflow,
            ProfessionalReportQuery reportService,
            ProfessionalAssessmentFormMapper formMapper,
            ProfessionalReportViewMapper reportViewMapper,
            ProfessionalReportPurchaseUseCase purchaseService) {
        this.workflow = workflow;
        this.reportService = reportService;
        this.formMapper = formMapper;
        this.reportViewMapper = reportViewMapper;
        this.purchaseService = purchaseService;
    }

    @GetMapping("/professional-assessment")
    public String showForm(Model model) {
        model.addAttribute("form", new ProfessionalAssessmentForm());
        return FORM_VIEW;
    }

    @GetMapping("/professional-assessment/example")
    public String showExample() {
        return "professional-assessment/professional-example";
    }

    @PostMapping("/professional-assessment")
    public String assess(
            @Valid @ModelAttribute("form") ProfessionalAssessmentForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            ProfessionalAssessmentState state = formMapper.toState(form);
            UUID id = workflow.submit(state, ProfessionalAssessmentInputMetadata.manual());
            return "redirect:/professional-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("Professional assessment could not be completed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }

    @GetMapping("/professional-assessment/{assessmentId}")
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
            return "professional-assessment/not-found";
        }
        try {
            var report = reportService.assemble(id, ProfessionalReportTier.FREE);
            if (report.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "professional-assessment/not-found";
            }
            model.addAttribute("assessmentId", id);
            model.addAttribute("report", reportViewMapper.toView(report.orElseThrow()));
            var purchase = ProfessionalPurchaseViewModel.from(purchaseService.accessFor(id));
            // The Stripe return only chooses which page to show. Access is still decided by the
            // entitlement that the verified webhook activated, and the report checks it again.
            boolean returnedFromCheckout = "returned".equals(checkout);
            if (returnedFromCheckout && purchase.active()) {
                return "redirect:/professional-assessment/" + id + "/report";
            }
            model.addAttribute("purchase", purchase);
            model.addAttribute("paymentPanel", paymentPanel(checkout));
            return "professional-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Saved professional assessment could not be loaded for id {}", id, exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
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
