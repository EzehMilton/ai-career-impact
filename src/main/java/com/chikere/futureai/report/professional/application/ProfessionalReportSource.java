package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceBundle;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;
import com.chikere.futureai.occupation.professional.application.StoredProfessionalOccupationClassification;

import java.util.Objects;
import java.util.Optional;

public record ProfessionalReportSource(
        StoredProfessionalAssessment assessment,
        Optional<StoredProfessionalNarrative> narrative,
        Optional<ProfessionalEvidenceBundle> evidence,
        Optional<StoredProfessionalOccupationClassification> classification) {

    public ProfessionalReportSource {
        Objects.requireNonNull(assessment); Objects.requireNonNull(narrative);
        Objects.requireNonNull(evidence); Objects.requireNonNull(classification);
    }
}
