package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGeneration;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceGathering;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationUseCase;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.chikere.futureai.cost.ProviderCostProperties;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ProfessionalAssessmentWorkflowTest {

    @Test
    void logsOneCostLineCoveringJudgmentAndNarrativeForTheSavedAssessment(CapturedOutput output) {
        var assessmentService = mock(ProfessionalAssessmentSubmission.class);
        var narrativeService = mock(ProfessionalNarrativeGeneration.class);
        var tracker = new ProviderCostTracker(new ProviderCostProperties(null, null, null));
        var state = new ProfessionalAssessmentState("Architect", "Designs software systems.",
                List.of("Review designs"), List.of("Architecture"), OptionalInt.empty());
        UUID id = UUID.randomUUID();
        when(assessmentService.submitProfessionalAssessment(state)).thenAnswer(invocation -> {
            tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 2_000, 0));
            return id;
        });
        when(narrativeService.generateFor(id)).thenAnswer(invocation -> {
            tracker.record(ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 3_000, 800));
            return new StoredProfessionalNarrative(id, NarrativeStatus.FAILED, Optional.empty(),
                    NarrativeSource.OPENAI, "openai", Optional.empty(), "professional-narrative-v1",
                    Instant.EPOCH, Optional.empty());
        });

        new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                mock(ProfessionalOccupationClassificationUseCase.class),
                mock(ProfessionalEvidenceGathering.class), JourneyMetrics.disabled(), tracker).submit(state);

        assertThat(output).contains("journey=professional assessmentId=" + id + " outcome=completed "
                + "jevCalls=1 jevInputTokens=2000", "openaiCalls=1 openaiInputTokens=3000");
    }

    @Test
    void recordedNarrativeFailureIncrementsFailureMetric() {
        var assessmentService = mock(ProfessionalAssessmentSubmission.class);
        var narrativeService = mock(ProfessionalNarrativeGeneration.class);
        var registry = new SimpleMeterRegistry();
        var metrics = new JourneyMetrics(registry);
        var state = new ProfessionalAssessmentState("Architect", "Designs software systems.",
                List.of("Review designs"), List.of("Architecture"), OptionalInt.empty());
        UUID id = UUID.randomUUID();
        var saved = new StoredProfessionalNarrative(id, NarrativeStatus.FAILED, Optional.empty(),
                NarrativeSource.LOCAL_FIXTURE, "fixture", Optional.empty(), "professional-narrative-v1",
                Instant.EPOCH, Optional.empty());
        when(assessmentService.submitProfessionalAssessment(state)).thenReturn(id);
        when(narrativeService.generateFor(id)).thenReturn(saved);
        assertThat(new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                mock(ProfessionalOccupationClassificationUseCase.class),
                mock(ProfessionalEvidenceGathering.class), metrics).submit(state)).isEqualTo(id);
        assertThat(registry.counter("narrative.failures", "journey", "professional").count()).isEqualTo(1.0);
    }

    @Test
    void narrativeInfrastructureFailureCannotTurnCommittedAssessmentIntoFailedSubmission() {
        var assessmentService = mock(ProfessionalAssessmentSubmission.class);
        var narrativeService = mock(ProfessionalNarrativeGeneration.class);
        var evidenceService = mock(ProfessionalEvidenceGathering.class);
        var classificationService = mock(ProfessionalOccupationClassificationUseCase.class);
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
        var assessmentService = mock(ProfessionalAssessmentSubmission.class);
        var narrativeService = mock(ProfessionalNarrativeGeneration.class);
        var evidenceService = mock(ProfessionalEvidenceGathering.class);
        var classificationService = mock(ProfessionalOccupationClassificationUseCase.class);
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
        var assessmentService = mock(ProfessionalAssessmentSubmission.class);
        var narrativeService = mock(ProfessionalNarrativeGeneration.class);
        var evidenceService = mock(ProfessionalEvidenceGathering.class);
        var classificationService = mock(ProfessionalOccupationClassificationUseCase.class);
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
