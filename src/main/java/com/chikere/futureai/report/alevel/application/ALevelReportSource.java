package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;

import java.util.Objects;
import java.util.Optional;

public record ALevelReportSource(
        StoredALevelAssessment assessment,
        Optional<StoredALevelNarrative> narrative,
        Optional<ALevelEvidenceBundle> evidence) {

    public ALevelReportSource {
        Objects.requireNonNull(assessment);
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(evidence);
    }
}
