package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ALevelAssessmentWorkflowTest {

    private static final UUID ID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f");

    @Test
    void persistsTheAssessmentBeforeAnyEnrichmentRuns() {
        var assessmentService = mock(ALevelAssessmentService.class);
        var narrativeService = mock(ALevelNarrativeService.class);
        var evidenceService = mock(ALevelEvidenceService.class);
        var state = state();
        when(assessmentService.submitALevelAssessment(state)).thenReturn(ID);

        UUID id = new ALevelAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        InOrder order = inOrder(assessmentService, narrativeService, evidenceService);
        order.verify(assessmentService).submitALevelAssessment(state);
        order.verify(narrativeService).generateFor(ID);
        order.verify(evidenceService).gatherFor(ID);
        order.verifyNoMoreInteractions();
    }

    @Test
    void narrativeOrEvidenceFailureNeverInvalidatesTheCommittedAssessment() {
        var assessmentService = mock(ALevelAssessmentService.class);
        var narrativeService = mock(ALevelNarrativeService.class);
        var evidenceService = mock(ALevelEvidenceService.class);
        var state = state();
        when(assessmentService.submitALevelAssessment(state)).thenReturn(ID);
        doThrow(new IllegalStateException("narrative store down")).when(narrativeService).generateFor(ID);
        doThrow(new IllegalStateException("evidence store down")).when(evidenceService).gatherFor(ID);

        UUID id = new ALevelAssessmentWorkflow(assessmentService, narrativeService, evidenceService)
                .submit(state);

        assertThat(id).isEqualTo(ID);
        verify(narrativeService).generateFor(ID);
        verify(evidenceService).gatherFor(ID);
    }

    @Test
    void assessmentFailureStopsBeforeEnrichment() {
        var assessmentService = mock(ALevelAssessmentService.class);
        var narrativeService = mock(ALevelNarrativeService.class);
        var evidenceService = mock(ALevelEvidenceService.class);
        var state = state();
        doThrow(new IllegalStateException("Jev unavailable"))
                .when(assessmentService).submitALevelAssessment(state);
        var workflow = new ALevelAssessmentWorkflow(assessmentService, narrativeService, evidenceService);

        assertThatThrownBy(() -> workflow.submit(state)).hasMessage("Jev unavailable");
        verifyNoInteractions(narrativeService, evidenceService);
    }

    private ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
    }
}
