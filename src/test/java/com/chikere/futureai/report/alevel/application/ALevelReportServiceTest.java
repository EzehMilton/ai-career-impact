package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.ALevelTestAssessments;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ALevelReportServiceTest {

    private static final UUID ID = UUID.fromString("6d7e8f9a-0b1c-4d2e-8f3a-4b5c6d7e8f9a");

    @Test
    void readsOnlyPersistencePortsAndReturnsEmptyForAnUnknownAssessment() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        var service = new ALevelReportService(assessmentStore, narrativeStore, evidenceStore,
                new ALevelReportAssembler());

        assertThat(service.assemble(ID, ALevelReportTier.FREE)).get()
                .extracting(ALevelReport::selectedSubjects)
                .isEqualTo(List.of("Mathematics", "Economics", "Geography"));

        UUID unknown = UUID.randomUUID();
        when(assessmentStore.findById(unknown)).thenReturn(Optional.empty());
        assertThat(service.assemble(unknown, ALevelReportTier.FREE)).isEmpty();
    }

    @Test
    void freeReportPresentationUsesTheStoredCreationTimeCutover() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var service = new ALevelReportService(assessmentStore, mock(ALevelNarrativeStore.class),
                mock(ALevelEvidenceStore.class), new ALevelReportAssembler());
        var base = ALevelTestAssessments.subjectProfile(ID, "maths_economics_geography", AssessmentSource.JEV);
        var historical = new StoredALevelAssessment(base.id(),
                ALevelReportPresentationVersions.V3_CUTOVER.minusSeconds(1), base.state(), base.judgments(),
                base.result(), base.modelIdentifier());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(historical));

        assertThat(service.subjectProfile(ID).orElseThrow().optionsKeptOpen().name())
                .isEqualTo("Options Kept Open");

        var newAssessment = new StoredALevelAssessment(base.id(), ALevelReportPresentationVersions.V3_CUTOVER,
                base.state(), base.judgments(), base.result(), base.modelIdentifier());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(newAssessment));

        assertThat(service.subjectProfile(ID).orElseThrow().optionsKeptOpen().name())
                .isEqualTo("Future study options");
    }

    @Test
    void presentationVersionIsIndependentOfProviderProvenance() {
        var openAi = ALevelTestAssessments.subjectProfile(ID, "maths_economics_geography", AssessmentSource.OPENAI);
        var jev = ALevelTestAssessments.subjectProfile(UUID.randomUUID(), "maths_economics_geography",
                AssessmentSource.JEV);

        assertThat(ALevelReportPresentationVersions.forAssessmentCreatedAt(openAi.createdAt()))
                .isEqualTo(ALevelReportVersions.V2);
        assertThat(ALevelReportPresentationVersions.forAssessmentCreatedAt(jev.createdAt()))
                .isEqualTo(ALevelReportVersions.V2);
    }

    private StoredALevelAssessment snapshot() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
