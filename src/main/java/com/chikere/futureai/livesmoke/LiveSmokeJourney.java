package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentForm;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentForm;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/** The three journeys a smoke case can exercise, with the normal routes and form each one already has. */
public enum LiveSmokeJourney {
    PROFESSIONAL("professional", "Professional", "/professional-assessment", "professional-assessment/form",
            ProfessionalAssessmentForm::new),
    UNIVERSITY("university", "University", "/university-assessment", "university-assessment/form",
            UniversityAssessmentForm::new),
    A_LEVEL("a-level", "A-level", "/a-level-assessment", "a-level-assessment/form", ALevelAssessmentForm::new);

    private final String slug;
    private final String label;
    private final String basePath;
    private final String formView;
    private final Supplier<Object> newForm;

    LiveSmokeJourney(String slug, String label, String basePath, String formView, Supplier<Object> newForm) {
        this.slug = slug;
        this.label = label;
        this.basePath = basePath;
        this.formView = formView;
        this.newForm = newForm;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    /** The journey's normal route: the form, its POST target and the prefix of every result URL. */
    public String basePath() {
        return basePath;
    }

    /** The journey's normal form template. */
    public String formView() {
        return formView;
    }

    /** A new, empty instance of the journey's normal form DTO. */
    public Object newForm() {
        return newForm.get();
    }

    public static Optional<LiveSmokeJourney> fromSlug(String slug) {
        return Arrays.stream(values()).filter(journey -> journey.slug.equals(slug)).findFirst();
    }
}
