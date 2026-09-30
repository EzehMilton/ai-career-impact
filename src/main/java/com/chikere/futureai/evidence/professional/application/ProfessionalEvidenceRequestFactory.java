package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.occupation.professional.application.OccupationClassificationSystem;
import com.chikere.futureai.occupation.professional.application.StoredProfessionalOccupationClassification;

import java.util.Optional;

public final class ProfessionalEvidenceRequestFactory {
    private final String geography;

    public ProfessionalEvidenceRequestFactory(String geography) {
        if (geography == null || geography.isBlank()) throw new IllegalArgumentException("geography must not be blank");
        this.geography = geography.strip();
    }

    public ProfessionalEvidenceRequest create(StoredProfessionalAssessment assessment,
            Optional<StoredProfessionalOccupationClassification> storedClassification) {
        var state = assessment.state();
        Optional<String> classification = storedClassification
                .map(StoredProfessionalOccupationClassification::classification)
                .filter(value -> value.classificationSystem() == OccupationClassificationSystem.SOC_2020)
                .map(value -> value.classificationCode());
        return new ProfessionalEvidenceRequest(assessment.id(), state.profession(), state.responsibilities(),
                state.skills(), geography, JourneyType.PROFESSIONAL, classification);
    }
}
