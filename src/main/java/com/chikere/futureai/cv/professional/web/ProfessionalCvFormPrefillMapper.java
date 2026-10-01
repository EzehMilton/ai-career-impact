package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.cv.professional.application.ProfessionalCvParseResult;
import org.springframework.stereotype.Component;

@Component
final class ProfessionalCvFormPrefillMapper {
    ProfessionalAssessmentForm toForm(ProfessionalCvParseResult result) {
        var form = new ProfessionalAssessmentForm();
        form.setProfession(result.profession().orElse(""));
        form.setWorkDescription(result.workDescription().orElse(""));
        form.setResponsibilities(String.join("\n", result.responsibilities()));
        form.setSkills(String.join("\n", result.skills()));
        if (result.yearsOfExperience().isPresent()) {
            form.setYearsOfExperience(result.yearsOfExperience().getAsInt());
        }
        return form;
    }
}
