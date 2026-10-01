package com.chikere.futureai.evidence.university.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;

/** Builds the evidence request from the saved snapshot and explicit configured geography. */
public final class UniversityEvidenceRequestFactory {
    private final String defaultGeography;

    public UniversityEvidenceRequestFactory(String defaultGeography) {
        if (defaultGeography == null || defaultGeography.isBlank()) {
            throw new IllegalArgumentException("defaultGeography must not be blank");
        }
        this.defaultGeography = defaultGeography.strip();
    }

    public UniversityEvidenceRequest create(StoredUniversityAssessment assessment) {
        var state = assessment.state();
        return new UniversityEvidenceRequest(assessment.id(), state.courseTitle(), state.keyModules(),
                state.skillsDeveloped(), state.preferredCareerAreas(), state.institution(),
                state.country().orElse(defaultGeography), JourneyType.UNIVERSITY);
    }
}
