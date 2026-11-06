package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.web.BoundedTextList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Web DTO shaped for HTML fields and Bean Validation, deliberately separate from the domain. */
public class UniversityAssessmentForm {

    @NotBlank(message = "Enter the course or degree title.")
    @Size(max = 160, message = "Keep the course title under 160 characters.")
    private String courseTitle = "";

    @NotBlank(message = "Tell us what the course covers.")
    @Size(min = 40, max = 4000,
            message = "Describe the course in at least 40 and no more than 4,000 characters.")
    private String courseDescription = "";

    @NotBlank(message = "Add at least one module or topic.")
    @Size(max = 4000, message = "Keep modules under 4,000 characters.")
    // Each module is analysed as its own entry in the paid report, so a repeated module could never be prepared.
    @BoundedTextList(maxItems = 40, maxItemLength = 200, distinct = true)
    private String keyModules = "";

    @NotBlank(message = "Add at least one skill you expect to develop.")
    @Size(max = 2000, message = "Keep skills under 2,000 characters.")
    @BoundedTextList(maxItems = 40, maxItemLength = 120)
    private String skillsDeveloped = "";

    @Size(max = 2000, message = "Keep career areas under 2,000 characters.")
    @BoundedTextList(maxItems = 20, maxItemLength = 120)
    private String preferredCareerAreas = "";

    @Size(max = 160, message = "Keep the institution under 160 characters.")
    private String institution = "";

    @Size(max = 80, message = "Keep the country under 80 characters.")
    private String country = "";

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getCourseDescription() {
        return courseDescription;
    }

    public void setCourseDescription(String courseDescription) {
        this.courseDescription = courseDescription;
    }

    public String getKeyModules() {
        return keyModules;
    }

    public void setKeyModules(String keyModules) {
        this.keyModules = keyModules;
    }

    public String getSkillsDeveloped() {
        return skillsDeveloped;
    }

    public void setSkillsDeveloped(String skillsDeveloped) {
        this.skillsDeveloped = skillsDeveloped;
    }

    public String getPreferredCareerAreas() {
        return preferredCareerAreas;
    }

    public void setPreferredCareerAreas(String preferredCareerAreas) {
        this.preferredCareerAreas = preferredCareerAreas;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
