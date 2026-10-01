package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads only persistence ports. It never runs an assessment. */
public final class ALevelReportService {
    private final ALevelAssessmentStore assessmentStore;
    private final ALevelNarrativeStore narrativeStore;
    private final ALevelEvidenceStore evidenceStore;
    private final ALevelReportAssembler assembler;

    public ALevelReportService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelEvidenceStore evidenceStore,
            ALevelReportAssembler assembler) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.assembler = Objects.requireNonNull(assembler);
    }

    public Optional<ALevelReport> assemble(UUID assessmentId, ALevelReportTier tier) {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(tier);
        return assessmentStore.findById(assessmentId).map(assessment -> assembler.assemble(
                new ALevelReportSource(assessment, narrativeStore.findByAssessmentId(assessmentId),
                        evidenceStore.findByAssessmentId(assessmentId)), tier));
    }
}
