package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentService;
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

@Controller
public final class ProfessionalAssessmentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentController.class);
    private static final String FORM_VIEW = "professional-assessment/form";

    private final ProfessionalAssessmentService assessmentService;
    private final ProfessionalAssessmentFormMapper formMapper;
    private final ProfessionalAssessmentViewMapper viewMapper;

    public ProfessionalAssessmentController(
            ProfessionalAssessmentService assessmentService,
            ProfessionalAssessmentFormMapper formMapper,
            ProfessionalAssessmentViewMapper viewMapper) {
        this.assessmentService = assessmentService;
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
            ProfessionalAssessmentResult result = assessmentService.assess(state);
            model.addAttribute("assessment", viewMapper.toView(state.profession(), result));
            return "professional-assessment/result";
        }
        catch (RuntimeException exception) {
            LOGGER.error("Professional assessment could not be completed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }
}
