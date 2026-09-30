package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;

import java.util.Optional;

public final class ProfessionalEvidenceRequestFactory {
    private final String geography;
    private final String configuredProfession;
    private final String configuredOccupationClassification;

    public ProfessionalEvidenceRequestFactory(
            String geography, String configuredProfession, String configuredOccupationClassification) {
        if (geography == null || geography.isBlank()) throw new IllegalArgumentException("geography must not be blank");
        this.geography = geography.strip();
        this.configuredProfession = configuredProfession == null ? "" : configuredProfession.strip();
        this.configuredOccupationClassification = configuredOccupationClassification == null
                ? "" : configuredOccupationClassification.strip();
        if (this.configuredProfession.isEmpty() != this.configuredOccupationClassification.isEmpty()) {
            throw new IllegalArgumentException("configured profession and occupation classification must be set together");
        }
    }

    public ProfessionalEvidenceRequest create(StoredProfessionalAssessment assessment) {
        var state = assessment.state();
        Optional<String> classification = state.profession().equals(configuredProfession)
                ? Optional.of(configuredOccupationClassification).filter(value -> !value.isEmpty())
                : Optional.empty();
        return new ProfessionalEvidenceRequest(assessment.id(), state.profession(), state.responsibilities(),
                state.skills(), geography, JourneyType.PROFESSIONAL, classification);
    }
}
