package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.web.BoundedTextList;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfessionalProfileEditForm {
    @NotBlank(message = "Enter your current or most recent role.")
    @Size(max = 120, message = "Keep the role under 120 characters.")
    private String currentRole = "";

    @NotBlank(message = "Add a short summary of your work.")
    @Size(min = 40, max = 4000, message = "Use between 40 and 4,000 characters.")
    private String roleSummary = "";

    @NotBlank(message = "Add at least one main responsibility.")
    @Size(max = 4000)
    @BoundedTextList(maxItems = 40, maxItemLength = 300)
    private String responsibilities = "";

    @Size(max = 2000)
    @BoundedTextList(maxItems = 40, maxItemLength = 120)
    private String technicalSkills = "";

    @Size(max = 2000)
    @BoundedTextList(maxItems = 40, maxItemLength = 120)
    private String humanSkills = "";

    @Min(value = 0, message = "Years of experience cannot be negative.")
    @Max(value = 70, message = "Enter 70 years or fewer.")
    private Integer yearsExperience;

    @AssertTrue(message = "Add at least one technical or human skill.")
    public boolean isSkillPresent() {
        return !technicalSkills.isBlank() || !humanSkills.isBlank();
    }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String value) { currentRole = value == null ? "" : value; }
    public String getRoleSummary() { return roleSummary; }
    public void setRoleSummary(String value) { roleSummary = value == null ? "" : value; }
    public String getResponsibilities() { return responsibilities; }
    public void setResponsibilities(String value) { responsibilities = value == null ? "" : value; }
    public String getTechnicalSkills() { return technicalSkills; }
    public void setTechnicalSkills(String value) { technicalSkills = value == null ? "" : value; }
    public String getHumanSkills() { return humanSkills; }
    public void setHumanSkills(String value) { humanSkills = value == null ? "" : value; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer value) { yearsExperience = value; }
}
