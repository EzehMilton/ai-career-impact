package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
public final class ProfessionalProfileAssessmentMapper {
    public ProfessionalAssessmentState toState(ProfessionalProfile profile) {
        var responsibilities = new LinkedHashSet<>(profile.responsibilities());
        responsibilities.addAll(profile.leadershipResponsibilities());
        responsibilities.addAll(profile.decisionMakingResponsibilities());
        var skills = new LinkedHashSet<>(profile.technicalSkills());
        skills.addAll(profile.humanSkills());
        skills.addAll(profile.domainContext());
        return new ProfessionalAssessmentState(profile.currentRole(), profile.roleSummary(),
                responsibilities.stream().toList(), skills.stream().toList(), profile.yearsExperience());
    }
}
