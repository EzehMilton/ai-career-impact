package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UniversityReportServiceTest {

    private static final UUID ID = UUID.fromString("3b4c5d6e-7f8a-4b9c-8d0e-1f2a3b4c5d6e");

    @Test
    void readsOnlyPersistencePortsAndReturnsEmptyForAnUnknownAssessment() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var narrativeStore = mock(UniversityNarrativeStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        var service = new UniversityReportService(assessmentStore, narrativeStore, evidenceStore,
                new UniversityReportAssembler());

        assertThat(service.assemble(ID, UniversityReportTier.FREE)).get()
                .extracting(UniversityReport::courseTitle).isEqualTo("Computer Science");

        UUID unknown = UUID.randomUUID();
        when(assessmentStore.findById(unknown)).thenReturn(Optional.empty());
        assertThat(service.assemble(unknown, UniversityReportTier.FREE)).isEmpty();
    }

    private StoredUniversityAssessment snapshot() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of(),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
