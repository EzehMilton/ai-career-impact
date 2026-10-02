package com.chikere.futureai.assessment.alevel.web;

import com.chikere.futureai.assessment.web.BoundedTextList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Web DTO shaped for HTML fields and Bean Validation, deliberately separate from the domain. */
public class ALevelAssessmentForm {

    @NotBlank(message = "Add the A-level subjects you are taking or considering.")
    @Size(max = 500, message = "Keep your subjects under 500 characters.")
    private String selectedSubjects = "";

    @Size(max = 500, message = "Keep predicted grades under 500 characters.")
    private String predictedGrades = "";

    @Size(max = 1000, message = "Keep this under 1,000 characters.")
    @BoundedTextList(maxItems = 20, maxItemLength = 200)
    private String strengthsAndInterests = "";

    @Size(max = 1000, message = "Keep degree areas under 1,000 characters.")
    @BoundedTextList(maxItems = 20, maxItemLength = 120)
    private String preferredDegreeAreas = "";

    @Size(max = 1000, message = "Keep career areas under 1,000 characters.")
    @BoundedTextList(maxItems = 20, maxItemLength = 120)
    private String preferredCareerAreas = "";

    @Size(max = 80, message = "Keep the country under 80 characters.")
    private String country = "";

    @Size(max = 2000, message = "Keep anything else under 2,000 characters.")
    private String additionalContext = "";

    public String getSelectedSubjects() {
        return selectedSubjects;
    }

    public void setSelectedSubjects(String selectedSubjects) {
        this.selectedSubjects = selectedSubjects;
    }

    public String getPredictedGrades() {
        return predictedGrades;
    }

    public void setPredictedGrades(String predictedGrades) {
        this.predictedGrades = predictedGrades;
    }

    public String getStrengthsAndInterests() {
        return strengthsAndInterests;
    }

    public void setStrengthsAndInterests(String strengthsAndInterests) {
        this.strengthsAndInterests = strengthsAndInterests;
    }

    public String getPreferredDegreeAreas() {
        return preferredDegreeAreas;
    }

    public void setPreferredDegreeAreas(String preferredDegreeAreas) {
        this.preferredDegreeAreas = preferredDegreeAreas;
    }

    public String getPreferredCareerAreas() {
        return preferredCareerAreas;
    }

    public void setPreferredCareerAreas(String preferredCareerAreas) {
        this.preferredCareerAreas = preferredCareerAreas;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAdditionalContext() {
        return additionalContext;
    }

    public void setAdditionalContext(String additionalContext) {
        this.additionalContext = additionalContext;
    }
}
