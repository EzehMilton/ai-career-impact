package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads only persistence ports. It never runs an assessment. */
public final class UniversityReportService implements UniversityReportQuery {
    private final UniversityAssessmentStore assessmentStore;
    private final UniversityNarrativeStore narrativeStore;
    private final UniversityEvidenceStore evidenceStore;
    private final UniversityReportAssembler assembler;
    private final AssessmentTrace trace;
    private final UniversityCourseProfileComposer profiles = new UniversityCourseProfileComposer();

    public UniversityReportService(UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore, UniversityEvidenceStore evidenceStore,
            UniversityReportAssembler assembler) {
        this(assessmentStore, narrativeStore, evidenceStore, assembler, AssessmentTrace.disabled());
    }

    public UniversityReportService(UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore, UniversityEvidenceStore evidenceStore,
            UniversityReportAssembler assembler, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.assembler = Objects.requireNonNull(assembler);
    }

    @Override
    public Optional<UniversityReport> assemble(UUID assessmentId, UniversityReportTier tier) {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(tier);
        return trace.report(JourneyType.UNIVERSITY, assessmentId, tier == UniversityReportTier.PAID,
                () -> assessmentStore.findById(assessmentId)
                        .filter(assessment -> !assessment.result().usesCourseProfile())
                        .map(assessment -> assembler.assemble(
                new UniversityReportSource(assessment, narrativeStore.findByAssessmentId(assessmentId),
                        evidenceStore.findByAssessmentId(assessmentId)), tier)));
    }

    /** Traced as the free result, exactly like the v1 free report, so both versions are observable. */
    @Override
    public Optional<UniversityCourseProfile> courseProfile(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        return trace.report(JourneyType.UNIVERSITY, assessmentId, false,
                () -> assessmentStore.findById(assessmentId)
                        .filter(assessment -> assessment.result().usesCourseProfile())
                        .map(assessment -> profiles.compose(assessment.result().profile())));
    }

    @Override
    public Optional<com.chikere.futureai.assessment.university.application.StoredUniversityAssessment> stored(
            UUID assessmentId) {
        return assessmentStore.findById(Objects.requireNonNull(assessmentId));
    }
}
