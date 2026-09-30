package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationService;
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
        var evidenceService = mock(ProfessionalEvidenceService.class);
        var classificationService = mock(ProfessionalOccupationClassificationService.class);
        var state = new ProfessionalAssessmentState("Electrician", "Installs electrical systems safely.",
                List.of("Install wiring"), List.of("Diagnostics"), OptionalInt.of(8));
        UUID id = UUID.randomUUID();
        when(assessmentService.submitProfessionalAssessment(state)).thenReturn(id);
        doThrow(new IllegalStateException("narrative database unavailable"))
                .when(narrativeService).generateFor(id);

        UUID submitted = new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                classificationService, evidenceService).submit(state);

        assertThat(submitted).isEqualTo(id);
        verify(assessmentService).submitProfessionalAssessment(state);
        verify(narrativeService).generateFor(id);
        verify(classificationService).classifyFor(id);
        verify(evidenceService).gatherFor(id);
    }

    @Test
    void evidenceInfrastructureFailureCannotInvalidateAssessmentOrNarrative() {
        var assessmentService = mock(ProfessionalAssessmentService.class);
        var narrativeService = mock(ProfessionalNarrativeService.class);
        var evidenceService = mock(ProfessionalEvidenceService.class);
        var classificationService = mock(ProfessionalOccupationClassificationService.class);
        var state = new ProfessionalAssessmentState("Nurse", "Provides direct patient care.",
                List.of("Assess patients"), List.of("Clinical judgment"), OptionalInt.of(6));
        UUID id = UUID.randomUUID();
        when(assessmentService.submitProfessionalAssessment(state)).thenReturn(id);
        doThrow(new IllegalStateException("evidence database unavailable")).when(evidenceService).gatherFor(id);

        assertThat(new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                classificationService, evidenceService)
                .submit(state)).isEqualTo(id);

        verify(assessmentService).submitProfessionalAssessment(state);
        verify(narrativeService).generateFor(id);
        verify(classificationService).classifyFor(id);
        verify(evidenceService).gatherFor(id);
    }

    @Test
    void classificationFailureCannotInvalidateAssessmentOrPreventEvidenceLifecycle() {
        var assessmentService = mock(ProfessionalAssessmentService.class);
        var narrativeService = mock(ProfessionalNarrativeService.class);
        var evidenceService = mock(ProfessionalEvidenceService.class);
        var classificationService = mock(ProfessionalOccupationClassificationService.class);
        var state = new ProfessionalAssessmentState("Electrician", "Installs and diagnoses electrical systems.",
                List.of("Diagnose faults"), List.of("Electrical safety"), OptionalInt.of(10));
        UUID id = UUID.randomUUID();
        when(assessmentService.submitProfessionalAssessment(state)).thenReturn(id);
        doThrow(new IllegalStateException("classification database unavailable"))
                .when(classificationService).classifyFor(id);

        assertThat(new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                classificationService, evidenceService).submit(state)).isEqualTo(id);

        verify(assessmentService).submitProfessionalAssessment(state);
        verify(narrativeService).generateFor(id);
        verify(classificationService).classifyFor(id);
        verify(evidenceService).gatherFor(id);
    }
}
