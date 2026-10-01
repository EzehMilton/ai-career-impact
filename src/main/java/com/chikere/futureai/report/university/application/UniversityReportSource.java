package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;

import java.util.Objects;
import java.util.Optional;

public record UniversityReportSource(
        StoredUniversityAssessment assessment,
        Optional<StoredUniversityNarrative> narrative,
        Optional<UniversityEvidenceBundle> evidence) {

    public UniversityReportSource {
        Objects.requireNonNull(assessment);
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(evidence);
    }
}
