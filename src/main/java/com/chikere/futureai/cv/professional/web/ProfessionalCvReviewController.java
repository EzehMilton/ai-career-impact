package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.web.SubmissionTokenGuard;
import com.chikere.futureai.assessment.professional.web.TokenStatus;
import com.chikere.futureai.cv.professional.application.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public final class ProfessionalCvReviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalCvReviewController.class);
    private static final String REVIEW_VIEW = "professional-assessment/cv-review";
    private static final String EDIT_VIEW = "professional-assessment/cv-edit";

    private final ProfessionalProfileEditMapper editMapper;
    private final ProfessionalProfileValidator validator;
    private final ProfessionalProfileAssessmentMapper assessmentMapper;
    private final ProfessionalAssessmentUseCase workflow;
    private final SubmissionTokenGuard tokenGuard;

    public ProfessionalCvReviewController(ProfessionalProfileEditMapper editMapper,
            ProfessionalProfileValidator validator, ProfessionalProfileAssessmentMapper assessmentMapper,
            ProfessionalAssessmentUseCase workflow, SubmissionTokenGuard tokenGuard) {
        this.editMapper = editMapper;
        this.validator = validator;
        this.assessmentMapper = assessmentMapper;
        this.workflow = workflow;
        this.tokenGuard = tokenGuard;
    }

    @GetMapping("/professional-assessment/cv/review")
    public String review(HttpSession session, Model model) {
        ProfessionalProfile profile = profile(session);
        if (profile == null) return "redirect:/professional-assessment";
        model.addAttribute("profile", profile);
        model.addAttribute("confirmToken", tokenGuard.issueToken(session));
        return REVIEW_VIEW;
    }

    @GetMapping("/professional-assessment/cv/edit")
    public String edit(HttpSession session, Model model) {
        ProfessionalProfile profile = profile(session);
        if (profile == null) return "redirect:/professional-assessment";
        model.addAttribute("profileForm", editMapper.toForm(profile));
        return EDIT_VIEW;
    }

    @PostMapping("/professional-assessment/cv/edit")
    public String saveEdit(@Valid @ModelAttribute("profileForm") ProfessionalProfileEditForm form,
            BindingResult bindingResult, HttpSession session, Model model) {
        ProfessionalProfile existing = profile(session);
        if (existing == null) return "redirect:/professional-assessment";
        if (bindingResult.hasErrors()) return EDIT_VIEW;
        try {
            session.setAttribute(ProfessionalCvSession.PROFILE, validator.validate(editMapper.update(existing, form)));
            return "redirect:/professional-assessment/cv/review";
        }
        catch (ProfessionalProfileExtractionException exception) {
            model.addAttribute("profileError", "Check the role, responsibilities and skills, then try again.");
            return EDIT_VIEW;
        }
    }

    @PostMapping("/professional-assessment/cv/confirm")
    public String confirm(
            @RequestParam(required = false, defaultValue = "") String confirmToken,
            HttpSession session, HttpServletResponse response) {
        TokenStatus before = tokenGuard.claimToken(session, confirmToken);
        if (before instanceof TokenStatus.Completed c) {
            return "redirect:/professional-assessment/" + c.assessmentId();
        }
        if (before instanceof TokenStatus.InProgress) {
            return "redirect:/professional-assessment/await?token=" + confirmToken;
        }

        ProfessionalProfile profile = profile(session);
        Object metadataValue = session.getAttribute(ProfessionalCvSession.INPUT_METADATA);
        if (profile == null || !(metadataValue instanceof ProfessionalAssessmentInputMetadata metadata)) {
            return "redirect:/professional-assessment";
        }

        try {
            var id = workflow.submit(assessmentMapper.toState(validator.validate(profile)), metadata);
            if (before instanceof TokenStatus.Registered) {
                tokenGuard.completeToken(session, confirmToken, id);
            }
            clear(session);
            LOGGER.info("Professional profile confirmed outcome=SUCCESS");
            return "redirect:/professional-assessment/" + id;
        }
        catch (RuntimeException exception) {
            LOGGER.error("Professional profile confirmation failed failureType={}",
                    exception.getClass().getSimpleName());
            if (before instanceof TokenStatus.Registered) {
                tokenGuard.freeToken(session, confirmToken);
            }
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "professional-assessment/error";
        }
    }

    private ProfessionalProfile profile(HttpSession session) {
        Object value = session.getAttribute(ProfessionalCvSession.PROFILE);
        return value instanceof ProfessionalProfile profile ? profile : null;
    }

    private void clear(HttpSession session) {
        session.removeAttribute(ProfessionalCvSession.PROFILE);
        session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
    }
}
