package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfessionalAssessmentWorkflowTest {

    @Test
    void narrativeInfrastructureFailureCannotTurnCommittedAssessmentIntoFailedSubmission() {
        var assessmentService = mock(ProfessionalAssessmentService.class);
        var narrativeService = mock(ProfessionalNarrativeService.class);
        var state = new ProfessionalAssessmentState("Electrician", "Installs electrical systems safely.",
                List.of("Install wiring"), List.of("Diagnostics"), OptionalInt.of(8));
        UUID id = UUID.randomUUID();
        when(assessmentService.submitProfessionalAssessment(state)).thenReturn(id);
        doThrow(new IllegalStateException("narrative database unavailable"))
                .when(narrativeService).generateFor(id);

        UUID submitted = new ProfessionalAssessmentWorkflow(assessmentService, narrativeService).submit(state);

        assertThat(submitted).isEqualTo(id);
        verify(assessmentService).submitProfessionalAssessment(state);
        verify(narrativeService).generateFor(id);
    }
}
