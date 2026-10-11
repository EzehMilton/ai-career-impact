package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.cv.professional.application.ProfessionalProfile;
import com.chikere.futureai.cv.professional.application.ProfessionalProfileCompleteness;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

@Component
final class ProfessionalProfileEditMapper {
    ProfessionalProfileEditForm toForm(ProfessionalProfile profile) {
        var form = new ProfessionalProfileEditForm();
        form.setCurrentRole(profile.currentRole());
        form.setCurrentEmployer(profile.currentEmployer());
        form.setCurrentRoleStart(profile.currentRoleStart());
        form.setCurrentRoleEnd(profile.currentRoleEnd());
        form.setRoleSummary(profile.roleSummary());
        form.setResponsibilities(String.join("\n", profile.responsibilities()));
        form.setTechnicalSkills(String.join("\n", profile.technicalSkills()));
        form.setHumanSkills(String.join("\n", profile.humanSkills()));
        if (profile.yearsExperience().isPresent()) form.setYearsExperience(profile.yearsExperience().getAsInt());
        return form;
    }

    ProfessionalProfile update(ProfessionalProfile existing, ProfessionalProfileEditForm form) {
        OptionalInt years = form.getYearsExperience() == null
                ? OptionalInt.empty() : OptionalInt.of(form.getYearsExperience());
        return new ProfessionalProfile(form.getCurrentRole(), form.getCurrentEmployer(), form.getCurrentRoleStart(),
                form.getCurrentRoleEnd(), form.getRoleSummary(), lines(form.getResponsibilities()),
                lines(form.getTechnicalSkills()), lines(form.getHumanSkills()),
                existing.leadershipResponsibilities(), existing.decisionMakingResponsibilities(),
                existing.domainContext(), years,
                // The basis explains a calculated number; once the user changes it, it no longer does.
                years.equals(existing.yearsExperience()) ? existing.experienceBasis() : List.of(),
                ProfessionalProfileCompleteness.COMPLETE, existing.warnings());
    }

    private List<String> lines(String value) {
        return Arrays.stream(value.split("\\R")).map(String::strip).filter(line -> !line.isEmpty()).toList();
    }
}
