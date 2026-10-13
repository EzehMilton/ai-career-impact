package com.chikere.futureai.assessment.professional.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.chikere.futureai.assessment.web.BoundedTextList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProfessionalAssessmentForm {

    @NotBlank(message = "Enter your job title.")
    @Size(max = 120, message = "Keep your job title under 120 characters.")
    private String profession = "";

    @NotBlank(message = "Describe what you actually do at work.")
    @Size(min = 40, max = 4000,
            message = "Describe your work in at least 40 and no more than 4,000 characters.")
    private String workDescription = "";

    @NotBlank(message = "Add at least one main responsibility.")
    @Size(max = 4000, message = "Keep responsibilities under 4,000 characters.")
    @BoundedTextList(maxItems = 40, maxItemLength = 300)
    private String responsibilities = "";

    @NotBlank(message = "Add at least one skill you use.")
    @Size(max = 2000, message = "Keep skills under 2,000 characters.")
    @BoundedTextList(maxItems = 40, maxItemLength = 120)
    private String skills = "";

    @Min(value = 0, message = "Years of experience cannot be negative.")
    @Max(value = 70, message = "Enter 70 years or fewer.")
    private Integer yearsOfExperience;

    private String submissionToken = "";

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getWorkDescription() {
        return workDescription;
    }

    public void setWorkDescription(String workDescription) {
        this.workDescription = workDescription;
    }

    public String getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(String responsibilities) {
        this.responsibilities = responsibilities;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getSubmissionToken() {
        return submissionToken;
    }

    public void setSubmissionToken(String submissionToken) {
        this.submissionToken = submissionToken;
    }
}
