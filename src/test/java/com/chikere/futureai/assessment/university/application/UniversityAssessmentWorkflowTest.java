package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceGathering;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGeneration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniversityAssessmentWorkflowTest {

    private static final UUID ID = UUID.fromString("1a28e0d8-9c3e-4e9f-9f4f-2e1a1d0a7b11");

    @Test
    void recordedNarrativeFailureIncrementsFailureMetric() {
        var assessmentService = mock(UniversityAssessmentSubmission.class);
        var narrativeService = mock(UniversityNarrativeGeneration.class);
        var registry = new SimpleMeterRegistry();
        var metrics = new JourneyMetrics(registry);
        var saved = new StoredUniversityNarrative(ID, NarrativeStatus.FAILED, Optional.empty(),
                NarrativeSource.LOCAL_FIXTURE, "fixture", Optional.empty(), "university-narrative-v1",
                Instant.EPOCH, Optional.empty());
        when(assessmentService.submitUniversityAssessment(state())).thenReturn(ID);
        when(narrativeService.generateIfApplicable(ID)).thenReturn(Optional.of(saved));
        assertThat(new UniversityAssessmentWorkflow(assessmentService, narrativeService,
                mock(UniversityEvidenceGathering.class), metrics).submit(state())).isEqualTo(ID);
        assertThat(registry.counter("narrative.failures", "journey", "university").count()).isEqualTo(1.0);
    }

    @Test
    void persistsTheAssessmentBeforeAnyEnrichmentRuns() {
        var assessmentService = mock(UniversityAssessmentSubmission.class);
        var narrativeService = mock(UniversityNarrativeGeneration.class);
        var evidenceService = mock(UniversityEvidenceGathering.class);
        var state = state();
        when(assessmentService.submitUniversityAssessment(state)).thenReturn(ID);

        UUID id = new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        InOrder order = inOrder(assessmentService, narrativeService, evidenceService);
        order.verify(assessmentService).submitUniversityAssessment(state);
        order.verify(narrativeService).generateIfApplicable(ID);
        order.verify(evidenceService).gatherFor(ID);
        order.verifyNoMoreInteractions();
    }

    @Test
    void narrativeOrEvidenceFailureNeverInvalidatesTheCommittedAssessment() {
        var assessmentService = mock(UniversityAssessmentSubmission.class);
        var narrativeService = mock(UniversityNarrativeGeneration.class);
        var evidenceService = mock(UniversityEvidenceGathering.class);
        var state = state();
        when(assessmentService.submitUniversityAssessment(state)).thenReturn(ID);
        doThrow(new IllegalStateException("narrative store down")).when(narrativeService).generateIfApplicable(ID);
        doThrow(new IllegalStateException("evidence store down")).when(evidenceService).gatherFor(ID);

        UUID id = new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        verify(narrativeService).generateIfApplicable(ID);
        verify(evidenceService).gatherFor(ID);
    }

    @Test
    void assessmentFailureStopsBeforeEnrichment() {
        var assessmentService = mock(UniversityAssessmentSubmission.class);
        var narrativeService = mock(UniversityNarrativeGeneration.class);
        var evidenceService = mock(UniversityEvidenceGathering.class);
        var state = state();
        doThrow(new IllegalStateException("Jev unavailable"))
                .when(assessmentService).submitUniversityAssessment(state);
        var workflow = new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.submit(state))
                .hasMessage("Jev unavailable");
        org.mockito.Mockito.verifyNoInteractions(narrativeService, evidenceService);
    }

    private UniversityAssessmentState state() {
        return new UniversityAssessmentState("A course", "Explicit local test case with enough detail.",
                List.of("A module"), List.of("A skill"), List.of(), Optional.empty(), Optional.empty());
    }
}
