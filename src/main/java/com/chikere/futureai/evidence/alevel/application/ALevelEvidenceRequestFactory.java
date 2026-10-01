package com.chikere.futureai.evidence.alevel.application;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.JourneyType;

/** Builds the evidence request from the saved snapshot and explicit configured geography. */
public final class ALevelEvidenceRequestFactory {
    private final String defaultGeography;

    public ALevelEvidenceRequestFactory(String defaultGeography) {
        if (defaultGeography == null || defaultGeography.isBlank()) {
            throw new IllegalArgumentException("defaultGeography must not be blank");
        }
        this.defaultGeography = defaultGeography.strip();
    }

    public ALevelEvidenceRequest create(StoredALevelAssessment assessment) {
        var state = assessment.state();
        return new ALevelEvidenceRequest(assessment.id(), state.selectedSubjects(),
                state.preferredDegreeAreas(), state.preferredCareerAreas(),
                state.country().orElse(defaultGeography), JourneyType.A_LEVEL);
    }
}
