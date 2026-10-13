package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.cv.professional.web.ProfessionalCvSession;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.professional.web.ProfessionalPurchaseViewModel;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
    private static final String IN_PROGRESS_VIEW = "professional-assessment/in-progress";

    private final ProfessionalAssessmentUseCase workflow;
    private final ProfessionalReportQuery reportService;
    private final ProfessionalAssessmentFormMapper formMapper;
    private final ProfessionalReportViewMapper reportViewMapper;
    private final ProfessionalReportPurchaseUseCase purchaseService;
    private final SubmissionTokenGuard tokenGuard;

    public ProfessionalAssessmentController(
            ProfessionalAssessmentUseCase workflow,
            ProfessionalReportQuery reportService,
            ProfessionalAssessmentFormMapper formMapper,
            ProfessionalReportViewMapper reportViewMapper,
            ProfessionalReportPurchaseUseCase purchaseService,
            SubmissionTokenGuard tokenGuard) {
        this.workflow = workflow;
        this.reportService = reportService;
        this.formMapper = formMapper;
        this.reportViewMapper = reportViewMapper;
        this.purchaseService = purchaseService;
        this.tokenGuard = tokenGuard;
    }

    @GetMapping("/professional-assessment")
    public String showForm(Model model, HttpSession session) {
        session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
        session.removeAttribute(ProfessionalCvSession.PROFILE);
        var form = new ProfessionalAssessmentForm();
        form.setSubmissionToken(tokenGuard.issueToken(session));
        model.addAttribute("form", form);
        return FORM_VIEW;
    }

    @PostMapping("/professional-assessment")
    public String assess(
            @Valid @ModelAttribute("form") ProfessionalAssessmentForm form,
            BindingResult bindingResult,
            Model model,
            HttpSession session,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        String token = form.getSubmissionToken();
        TokenStatus before = tokenGuard.claimToken(session, token);

        if (before instanceof TokenStatus.Completed c) {
            return "redirect:/professional-assessment/" + c.assessmentId();
        }
        if (before instanceof TokenStatus.InProgress) {
            return "redirect:/professional-assessment/await?token=" + token;
        }

        // before is Registered (now claimed as InProgress) or null (unknown token — no idempotency)
        try {
            ProfessionalAssessmentState state = formMapper.toState(form);
            UUID id = workflow.submit(state, ProfessionalAssessmentInputMetadata.manual());
            if (before instanceof TokenStatus.Registered) {
                tokenGuard.completeToken(session, token, id);
            }
            session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
            session.removeAttribute(ProfessionalCvSession.PROFILE);
            return "redirect:/professional-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("Professional assessment could not be completed", exception);
            if (before instanceof TokenStatus.Registered) {
                tokenGuard.freeToken(session, token);
            }
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }

    @GetMapping("/professional-assessment/await")
    public String awaitAssessment(@RequestParam String token, HttpSession session) {
        TokenStatus status = tokenGuard.statusOf(session, token);
        if (status instanceof TokenStatus.Completed c) {
            return "redirect:/professional-assessment/" + c.assessmentId();
        }
        if (status instanceof TokenStatus.InProgress) {
            return IN_PROGRESS_VIEW;
        }
        return "redirect:/professional-assessment";
    }

    @GetMapping("/professional-assessment/{assessmentId}")
    public String showResult(@PathVariable String assessmentId, Model model, HttpServletResponse response) {
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
            model.addAttribute("purchase", ProfessionalPurchaseViewModel.from(purchaseService.accessFor(id)));
            return "professional-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Saved professional assessment could not be loaded for id {}", id, exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }
}
