package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceService;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniversityAssessmentWorkflowTest {

    private static final UUID ID = UUID.fromString("1a28e0d8-9c3e-4e9f-9f4f-2e1a1d0a7b11");

    @Test
    void persistsTheAssessmentBeforeAnyEnrichmentRuns() {
        var assessmentService = mock(UniversityAssessmentService.class);
        var narrativeService = mock(UniversityNarrativeService.class);
        var evidenceService = mock(UniversityEvidenceService.class);
        var state = state();
        when(assessmentService.submitUniversityAssessment(state)).thenReturn(ID);

        UUID id = new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        InOrder order = inOrder(assessmentService, narrativeService, evidenceService);
        order.verify(assessmentService).submitUniversityAssessment(state);
        order.verify(narrativeService).generateFor(ID);
        order.verify(evidenceService).gatherFor(ID);
        order.verifyNoMoreInteractions();
    }

    @Test
    void narrativeOrEvidenceFailureNeverInvalidatesTheCommittedAssessment() {
        var assessmentService = mock(UniversityAssessmentService.class);
        var narrativeService = mock(UniversityNarrativeService.class);
        var evidenceService = mock(UniversityEvidenceService.class);
        var state = state();
        when(assessmentService.submitUniversityAssessment(state)).thenReturn(ID);
        doThrow(new IllegalStateException("narrative store down")).when(narrativeService).generateFor(ID);
        doThrow(new IllegalStateException("evidence store down")).when(evidenceService).gatherFor(ID);

        UUID id = new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        verify(narrativeService).generateFor(ID);
        verify(evidenceService).gatherFor(ID);
    }

    @Test
    void assessmentFailureStopsBeforeEnrichment() {
        var assessmentService = mock(UniversityAssessmentService.class);
        var narrativeService = mock(UniversityNarrativeService.class);
        var evidenceService = mock(UniversityEvidenceService.class);
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
