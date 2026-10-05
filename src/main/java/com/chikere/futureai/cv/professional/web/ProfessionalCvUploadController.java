package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.cv.professional.application.ProfessionalProfileExtractionException;
import com.chikere.futureai.cv.professional.application.ProfessionalCvParsingException;
import com.chikere.futureai.cv.professional.application.ProfessionalCvAnalysisUseCase;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public final class ProfessionalCvUploadController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalCvUploadController.class);
    private static final String FORM_VIEW = "professional-assessment/form";
    private final ProfessionalCvUploadValidator validator;
    private final ProfessionalCvAnalysisUseCase uploadService;

    public ProfessionalCvUploadController(ProfessionalCvUploadValidator validator,
            ProfessionalCvAnalysisUseCase uploadService) {
        this.validator = validator;
        this.uploadService = uploadService;
    }

    // An upload that exceeds the configured size never reaches this method: the multipart parser
    // rejects it before a handler is chosen, so it is answered by the shared 413 page instead.
    // The validator's size check below still matters — it bounds what the parser is asked to read.
    @PostMapping("/professional-assessment/cv")
    public String upload(@RequestParam("cv") MultipartFile file, Model model,
            HttpSession session, HttpServletResponse response) {
        session.removeAttribute(ProfessionalCvSession.INPUT_METADATA);
        session.removeAttribute(ProfessionalCvSession.PROFILE);
        try {
            var document = validator.validate(file);
            LOGGER.info("Professional CV uploaded fileType={}", document.type());
            var result = uploadService.extractProfile(document);
            LOGGER.info("Professional profile extraction succeeded fileType={} outcome=SUCCESS", document.type());
            session.setAttribute(ProfessionalCvSession.INPUT_METADATA, result.inputMetadata());
            session.setAttribute(ProfessionalCvSession.PROFILE, result.profile());
            return "redirect:/professional-assessment/cv/review";
        }
        catch (ProfessionalCvUploadValidationException exception) {
            response.setStatus(exception.status());
            return errorForm(model, exception.getMessage());
        }
        catch (ProfessionalCvParsingException exception) {
            LOGGER.warn("Professional CV parsed outcome=FAILED failureType={}", exception.getClass().getSimpleName());
            response.setStatus(422);
            return errorForm(model, "We couldn't read this CV safely. Describe your work manually instead.");
        }
        catch (ProfessionalProfileExtractionException exception) {
            LOGGER.warn("Professional profile extraction failed outcome=FAILED failureType={}",
                    exception.getClass().getSimpleName());
            response.setStatus(422);
            return errorForm(model, "We couldn't identify enough reliable career information from this CV. Describe your work manually instead.");
        }
    }

    private String errorForm(Model model, String message) {
        model.addAttribute("form", new ProfessionalAssessmentForm());
        model.addAttribute("cvError", message);
        return FORM_VIEW;
    }
}
