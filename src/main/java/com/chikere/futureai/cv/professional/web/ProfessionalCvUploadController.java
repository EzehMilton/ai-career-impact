package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.cv.professional.application.ProfessionalCvParseStatus;
import com.chikere.futureai.cv.professional.application.ProfessionalCvParsingException;
import com.chikere.futureai.cv.professional.application.ProfessionalCvUploadService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Controller
public final class ProfessionalCvUploadController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalCvUploadController.class);
    private static final String FORM_VIEW = "professional-assessment/form";
    private final ProfessionalCvUploadValidator validator;
    private final ProfessionalCvUploadService uploadService;
    private final ProfessionalCvFormPrefillMapper formMapper;

    public ProfessionalCvUploadController(ProfessionalCvUploadValidator validator,
            ProfessionalCvUploadService uploadService, ProfessionalCvFormPrefillMapper formMapper) {
        this.validator = validator;
        this.uploadService = uploadService;
        this.formMapper = formMapper;
    }

    @PostMapping("/professional-assessment/cv")
    public String upload(@RequestParam("cv") MultipartFile file, Model model,
            HttpSession session, HttpServletResponse response) {
        session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
        try {
            var prefill = uploadService.parse(validator.validate(file));
            model.addAttribute("form", formMapper.toForm(prefill.result()));
            if (prefill.result().status() == ProfessionalCvParseStatus.INSUFFICIENT_TEXT) {
                model.addAttribute("cvError", prefill.result().warnings().getFirst());
                return FORM_VIEW;
            }
            model.addAttribute("cvPrefill", true);
            model.addAttribute("cvFilename", prefill.inputMetadata().originalFilename().orElse("CV"));
            model.addAttribute("cvWarnings", prefill.result().warnings());
            session.setAttribute(ProfessionalCvSession.INPUT_METADATA, prefill.inputMetadata());
            return FORM_VIEW;
        }
        catch (ProfessionalCvUploadValidationException exception) {
            response.setStatus(exception.status());
            return errorForm(model, exception.getMessage());
        }
        catch (ProfessionalCvParsingException exception) {
            LOGGER.warn("CV parsing failed safely ({})", exception.getClass().getSimpleName());
            response.setStatus(422);
            return errorForm(model, "We couldn't extract enough information from this CV. Please enter your work details manually.");
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MaxUploadSizeExceededException.class)
    public String oversized(Model model, HttpSession session, HttpServletResponse response) {
        session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
        response.setStatus(413);
        return errorForm(model, "The CV is too large. Upload a file no larger than 5 MiB.");
    }

    private String errorForm(Model model, String message) {
        model.addAttribute("form", new ProfessionalAssessmentForm());
        model.addAttribute("cvError", message);
        return FORM_VIEW;
    }
}
