package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfessionalNarrativeServiceTest {

    private static final UUID ID = UUID.fromString("1f8b20d9-9982-4f1a-bb42-98de4157013c");
    private final ProfessionalAssessmentStore assessmentStore = mock(ProfessionalAssessmentStore.class);
    private final ProfessionalNarrativeStore narrativeStore = mock(ProfessionalNarrativeStore.class);
    private final ProfessionalNarrativeGenerator generator = mock(ProfessionalNarrativeGenerator.class);

    @Test
    void buildsContextFromSavedSnapshotCallsGeneratorOnceAndPersistsVersionedNarrative() {
        var snapshot = snapshot();
        var narrative = narrative();
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot));
        metadata();
        when(generator.generate(any())).thenReturn(narrative);

        var result = service().generateFor(ID);

        ArgumentCaptor<ProfessionalNarrativeContext> context =
                ArgumentCaptor.forClass(ProfessionalNarrativeContext.class);
        verify(generator, times(1)).generate(context.capture());
        assertThat(context.getValue().profession()).isEqualTo("Software Architect");
        assertThat(context.getValue().workDescription()).contains("consequential architecture decisions");
        assertThat(context.getValue().responsibilities()).containsExactly("Design platforms", "Resolve trade-offs");
        assertThat(context.getValue().skills()).containsExactly("System design", "Mentoring");
        assertThat(context.getValue().yearsExperience()).hasValue(14);
        assertThat(context.getValue().measures()).hasSize(4);
        assertThat(context.getValue().exposureDrivers()).isNotEmpty();
        assertThat(context.getValue().protectiveFactors()).isNotEmpty();
        assertThat(context.getValue().assessmentSource()).isEqualTo(AssessmentSource.JEV);
        assertThat(context.getValue().judgmentSchemaVersion()).isEqualTo("professional-v1");
        assertThat(context.getValue().decisionPolicyVersion()).isEqualTo("professional-decision-v1");

        ArgumentCaptor<StoredProfessionalNarrative> saves =
                ArgumentCaptor.forClass(StoredProfessionalNarrative.class);
        verify(narrativeStore, times(2)).save(saves.capture());
        assertThat(saves.getAllValues()).extracting(StoredProfessionalNarrative::status)
                .containsExactly(NarrativeStatus.PENDING, NarrativeStatus.AVAILABLE);
        assertThat(result.narrative()).contains(narrative);
        assertThat(result.promptSchemaVersion()).isEqualTo("professional-narrative-v2");
        assertThat(result.source()).isEqualTo(NarrativeSource.OPENAI);
        assertThat(result.providerIdentifier()).isEqualTo("openai");
        assertThat(result.modelIdentifier()).contains("gpt-test");
        assertThat(result.generatedAt()).contains(Instant.parse("2026-09-30T12:00:00Z"));
    }

    @Test
    void generatorFailureStoresFailedAndNeverInvokesAnotherGenerator() {
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        metadata();
        when(generator.generate(any())).thenThrow(new IllegalStateException("provider secret"));

        StoredProfessionalNarrative result = service().generateFor(ID);

        assertThat(result.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(result.narrative()).isEmpty();
        verify(generator, times(1)).generate(any());
        ArgumentCaptor<StoredProfessionalNarrative> saves =
                ArgumentCaptor.forClass(StoredProfessionalNarrative.class);
        verify(narrativeStore, times(2)).save(saves.capture());
        assertThat(saves.getAllValues()).extracting(StoredProfessionalNarrative::status)
                .containsExactly(NarrativeStatus.PENDING, NarrativeStatus.FAILED);
        verify(assessmentStore).findById(ID);
    }

    @Test
    void savedHistoricalNarrativeIsReturnedWithoutLoadingAssessmentOrRegenerating() {
        StoredProfessionalNarrative historical = available(narrative(), "professional-narrative-v0");
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

        StoredProfessionalNarrative result = service().generateFor(ID);

        assertThat(result).isSameAs(historical);
        assertThat(result.promptSchemaVersion()).isEqualTo("professional-narrative-v0");
        verifyNoInteractions(assessmentStore, generator);
        verify(narrativeStore, never()).save(any());
    }

    private ProfessionalNarrativeService service() {
        return new ProfessionalNarrativeService(assessmentStore, narrativeStore, generator,
                new ProfessionalNarrativeContextFactory(), new ProfessionalNarrativeValidator(),
                Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC));
    }

    private void metadata() {
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        when(generator.modelIdentifier()).thenReturn(Optional.of("gpt-test"));
    }

    private StoredProfessionalAssessment snapshot() {
        var state = new ProfessionalAssessmentState("Software Architect",
                "I design software and make consequential architecture decisions.",
                List.of("Design platforms", "Resolve trade-offs"),
                List.of("System design", "Mentoring"), OptionalInt.of(14));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.JEV,
                judgments.schemaVersion(), decision.policyVersion());
        return new StoredProfessionalAssessment(ID, Instant.parse("2026-09-30T11:00:00Z"),
                state, judgments, result, Optional.of("jev-test"));
    }

    private ProfessionalNarrative narrative() {
        return new ProfessionalNarrative("A measured outlook", "Saved summary", "Saved meaning",
                "Saved exposure", "Saved human advantage", "Saved complementarity", "Saved adaptability",
                List.of("Review repeatable work", "Strengthen human context", "Document accountability"),
                "This is not a prediction.");
    }

    private StoredProfessionalNarrative available(ProfessionalNarrative value, String version) {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        return new StoredProfessionalNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(value),
                NarrativeSource.OPENAI, "openai", Optional.of("old-model"), version, time, Optional.of(time));
    }
}
