package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads only persistence ports. It never runs an assessment. */
public final class UniversityReportService {
    private final UniversityAssessmentStore assessmentStore;
    private final UniversityNarrativeStore narrativeStore;
    private final UniversityEvidenceStore evidenceStore;
    private final UniversityReportAssembler assembler;

    public UniversityReportService(UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore, UniversityEvidenceStore evidenceStore,
            UniversityReportAssembler assembler) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.assembler = Objects.requireNonNull(assembler);
    }

    public Optional<UniversityReport> assemble(UUID assessmentId, UniversityReportTier tier) {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(tier);
        return assessmentStore.findById(assessmentId).map(assessment -> assembler.assemble(
                new UniversityReportSource(assessment, narrativeStore.findByAssessmentId(assessmentId),
                        evidenceStore.findByAssessmentId(assessmentId)), tier));
    }
}
