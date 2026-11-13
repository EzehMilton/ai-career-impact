package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads only persistence ports. It never runs an assessment. */
public final class ALevelReportService implements ALevelReportQuery {
    private final ALevelAssessmentStore assessmentStore;
    private final ALevelNarrativeStore narrativeStore;
    private final ALevelEvidenceStore evidenceStore;
    private final ALevelReportAssembler assembler;
    private final AssessmentTrace trace;
    private final ALevelSubjectProfileComposer v2Profiles = new ALevelSubjectProfileComposer();
    private final ALevelSubjectProfileComposerV3 v3Profiles = new ALevelSubjectProfileComposerV3();

    public ALevelReportService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelEvidenceStore evidenceStore,
            ALevelReportAssembler assembler) {
        this(assessmentStore, narrativeStore, evidenceStore, assembler, AssessmentTrace.disabled());
    }

    public ALevelReportService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelEvidenceStore evidenceStore,
            ALevelReportAssembler assembler, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.narrativeStore = Objects.requireNonNull(narrativeStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.assembler = Objects.requireNonNull(assembler);
    }

    @Override
    public Optional<ALevelReport> assemble(UUID assessmentId, ALevelReportTier tier) {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(tier);
        return trace.report(JourneyType.A_LEVEL, assessmentId, tier == ALevelReportTier.PAID,
                () -> assessmentStore.findById(assessmentId)
                        .filter(assessment -> !assessment.result().usesSubjectProfile())
                        .map(assessment -> assembler.assemble(
                new ALevelReportSource(assessment, narrativeStore.findByAssessmentId(assessmentId),
                        evidenceStore.findByAssessmentId(assessmentId)), tier)));
    }

    /** Traced as the free result, exactly like the v1 free report, so both versions are observable. */
    @Override
    public Optional<ALevelSubjectProfile> subjectProfile(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        return trace.report(JourneyType.A_LEVEL, assessmentId, false,
                () -> assessmentStore.findById(assessmentId)
                        .filter(assessment -> assessment.result().usesSubjectProfile())
                        .map(assessment -> ALevelReportVersions.V3.equals(
                                ALevelReportPresentationVersions.forAssessmentCreatedAt(assessment.createdAt()))
                                ? v3Profiles.compose(assessment.result().profile())
                                : v2Profiles.compose(assessment.result().profile())));
    }

    @Override
    public Optional<StoredALevelAssessment> stored(UUID assessmentId) {
        return assessmentStore.findById(Objects.requireNonNull(assessmentId));
    }
}
