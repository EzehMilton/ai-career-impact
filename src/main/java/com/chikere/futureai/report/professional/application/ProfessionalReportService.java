package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalReportService implements ProfessionalReportQuery {
    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalNarrativeStore narrativeStore;
    private final ProfessionalEvidenceStore evidenceStore;
    private final ProfessionalOccupationClassificationStore classificationStore;
    private final ProfessionalReportAssembler assembler;
    private final AssessmentTrace trace;

    public ProfessionalReportService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore, ProfessionalEvidenceStore evidenceStore,
            ProfessionalOccupationClassificationStore classificationStore, ProfessionalReportAssembler assembler) {
        this(assessmentStore, narrativeStore, evidenceStore, classificationStore, assembler,
                AssessmentTrace.disabled());
    }

    public ProfessionalReportService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore, ProfessionalEvidenceStore evidenceStore,
            ProfessionalOccupationClassificationStore classificationStore, ProfessionalReportAssembler assembler,
                    AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.classificationStore = Objects.requireNonNull(classificationStore);
        this.assembler = Objects.requireNonNull(assembler);
    }

    @Override
    public Optional<ProfessionalReport> assemble(UUID assessmentId, ProfessionalReportTier tier) {
        Objects.requireNonNull(assessmentId); Objects.requireNonNull(tier);
        return trace.report(JourneyType.PROFESSIONAL, assessmentId, tier == ProfessionalReportTier.PAID,
                () -> assessmentStore.findById(assessmentId).map(assessment -> assembler.assemble(
                new ProfessionalReportSource(assessment, narrativeStore.findByAssessmentId(assessmentId),
                        evidenceStore.findByAssessmentId(assessmentId),
                        classificationStore.findByAssessmentId(assessmentId)), tier)));
    }
}
