package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentService;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentWorkflow;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
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

import java.util.UUID;

@Controller
public final class ProfessionalAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentController.class);
    private static final String FORM_VIEW = "professional-assessment/form";

    private final ProfessionalAssessmentService assessmentService;
    private final ProfessionalAssessmentWorkflow workflow;
    private final ProfessionalNarrativeService narrativeService;
    private final ProfessionalAssessmentFormMapper formMapper;
    private final ProfessionalAssessmentViewMapper viewMapper;

    public ProfessionalAssessmentController(
            ProfessionalAssessmentService assessmentService,
            ProfessionalAssessmentWorkflow workflow,
            ProfessionalNarrativeService narrativeService,
            ProfessionalAssessmentFormMapper formMapper,
            ProfessionalAssessmentViewMapper viewMapper) {
        this.assessmentService = assessmentService;
        this.workflow = workflow;
        this.narrativeService = narrativeService;
        this.formMapper = formMapper;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/professional-assessment")
    public String showForm(Model model) {
        model.addAttribute("form", new ProfessionalAssessmentForm());
        return FORM_VIEW;
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
            UUID id = workflow.submit(state);
            return "redirect:/professional-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("Professional assessment could not be completed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
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
            var saved = assessmentService.getProfessionalAssessment(id);
            if (saved.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return "professional-assessment/not-found";
            }
            var snapshot = saved.orElseThrow();
            model.addAttribute("assessment", viewMapper.toView(
                    snapshot, narrativeService.findFor(id)));
            return "professional-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Saved professional assessment could not be loaded for id {}", id, exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }
}
