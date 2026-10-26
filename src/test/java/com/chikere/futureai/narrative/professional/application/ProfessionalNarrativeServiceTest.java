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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
        assertThat(result.promptSchemaVersion()).isEqualTo("professional-narrative-v4");
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
    void anUnsafeNarrativeIsStoredAsFailedSoTheVisitorGetsTheFallbackAndTheAssessmentIsUntouched() {
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        metadata();
        var safe = narrative();
        when(generator.generate(any())).thenReturn(new ProfessionalNarrative(safe.headline(), safe.summary(),
                "Current AI developments are not expected to fundamentally alter the core responsibilities of your role.",
                safe.exposureExplanation(), safe.humanAdvantageExplanation(), safe.complementarityExplanation(),
                safe.adaptabilityExplanation(), safe.practicalActions(), safe.cautionOrLimitations()));

        StoredProfessionalNarrative result = service().generateFor(ID);

        assertThat(result.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(result.narrative()).as("rejected prose is never stored as the narrative").isEmpty();
        verify(generator, times(1)).generate(any());
        verify(assessmentStore).findById(ID);
        verifyNoMoreInteractions(assessmentStore);
    }

    // ---- One bounded repair attempt ----

    private static final String FORECAST =
            "Current AI developments are not expected to fundamentally alter the core responsibilities of your role.";

    @Test
    void aValidFirstNarrativeMakesExactlyOneProviderCallAndNoRepair() {
        prepare();
        when(generator.generate(any())).thenReturn(narrative());

        assertThat(service().generateFor(ID).status()).isEqualTo(NarrativeStatus.AVAILABLE);

        verify(generator, times(1)).generate(any());
        verify(generator, never()).repair(any(), any());
    }

    @Test
    void anInvalidFirstNarrativeIsRepairedOnceAndOnlyTheRepairedNarrativeIsSaved() {
        prepare();
        when(generator.generate(any())).thenReturn(withMeaning(FORECAST));
        var repaired = narrative();
        when(generator.repair(any(), any())).thenReturn(repaired);

        var result = service().generateFor(ID);

        assertThat(result.status()).isEqualTo(NarrativeStatus.AVAILABLE);
        assertThat(result.narrative()).contains(repaired);
        verify(generator, times(1)).generate(any());
        ArgumentCaptor<NarrativeRepairFeedback> feedback = ArgumentCaptor.forClass(NarrativeRepairFeedback.class);
        verify(generator, times(1)).repair(any(), feedback.capture());
        assertThat(feedback.getValue().rule()).isEqualTo("NO_UNSUPPORTED_FUTURE_PREDICTIONS");
        assertThat(feedback.getValue().offendingSentence()).isEqualTo(FORECAST);
        assertThat(feedback.getValue().reason()).contains("not forecast its future");
        ArgumentCaptor<StoredProfessionalNarrative> saves = ArgumentCaptor.forClass(StoredProfessionalNarrative.class);
        verify(narrativeStore, times(2)).save(saves.capture());
        assertThat(saves.getAllValues()).extracting(StoredProfessionalNarrative::status)
                .containsExactly(NarrativeStatus.PENDING, NarrativeStatus.AVAILABLE);
        assertThat(saves.getAllValues().toString()).as("the rejected draft is never persisted").doesNotContain(FORECAST);
        verify(assessmentStore).findById(ID);
        verifyNoMoreInteractions(assessmentStore);
    }

    @Test
    void aRepairThatAlsoFailsIsStoredAsFailedAfterExactlyTwoCalls() {
        prepare();
        when(generator.generate(any())).thenReturn(withMeaning(FORECAST));
        when(generator.repair(any(), any())).thenReturn(withMeaning("This role is safe from AI."));

        var result = service().generateFor(ID);

        assertThat(result.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(result.narrative()).isEmpty();
        verify(generator, times(1)).generate(any());
        verify(generator, times(1)).repair(any(), any());
        verify(assessmentStore).findById(ID);
        verifyNoMoreInteractions(assessmentStore);
    }

    @Test
    void aMissingNarrativeOrAProviderFailureIsNotRepaired() {
        prepare();
        when(generator.generate(any())).thenReturn(null);
        assertThat(service().generateFor(ID).status()).isEqualTo(NarrativeStatus.FAILED);
        verify(generator, never()).repair(any(), any());

        org.mockito.Mockito.reset(generator, narrativeStore);
        prepare();
        when(generator.generate(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThat(service().generateFor(ID).status()).isEqualTo(NarrativeStatus.FAILED);
        verify(generator, never()).repair(any(), any());
    }

    @Test
    void theTraceShowsBothAttemptsTheFailedRuleAndTheFinalStatus() {
        prepare();
        when(generator.generate(any())).thenReturn(withMeaning(FORECAST));
        when(generator.repair(any(), any())).thenReturn(narrative());
        var trace = com.chikere.futureai.trace.AssessmentTrace.enabled();
        var traced = new ProfessionalNarrativeService(assessmentStore, narrativeStore, generator,
                new ProfessionalNarrativeContextFactory(), new ProfessionalNarrativeValidator(trace),
                Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC),
                com.chikere.futureai.configuration.JourneyMetrics.disabled(), trace);

        try (var log = com.chikere.futureai.trace.TraceLogCapture.start()) {
            traced.generateFor(ID);

            assertThat(log.stages()).containsExactly("NARRATIVE_REQUEST", "NARRATIVE_RESPONSE", "NARRATIVE_VALIDATION",
                    "NARRATIVE_REPAIR_REQUEST", "NARRATIVE_RESPONSE", "NARRATIVE_VALIDATION", "NARRATIVE_OUTCOME");
            var responses = log.events(com.chikere.futureai.trace.TraceStage.NARRATIVE_RESPONSE);
            assertThat(responses).extracting(event -> event.at("/payload/result/attempt").asInt()).containsExactly(1, 2);
            var validations = log.events(com.chikere.futureai.trace.TraceStage.NARRATIVE_VALIDATION);
            assertThat(validations.get(0).at("/payload/outcome").asText()).isEqualTo("REJECTED");
            assertThat(validations.get(0).at("/payload/rejectedBy").asText()).isEqualTo("NO_UNSUPPORTED_FUTURE_PREDICTIONS");
            assertThat(validations.get(1).at("/payload/outcome").asText()).isEqualTo("ACCEPTED");
            var repair = log.only(com.chikere.futureai.trace.TraceStage.NARRATIVE_REPAIR_REQUEST).get("payload");
            assertThat(repair.get("failedRule").asText()).isEqualTo("NO_UNSUPPORTED_FUTURE_PREDICTIONS");
            assertThat(repair.get("offendingSentence").asText()).isEqualTo(FORECAST);
            var outcome = log.only(com.chikere.futureai.trace.TraceStage.NARRATIVE_OUTCOME).get("payload");
            assertThat(outcome.get("status").asText()).isEqualTo("AVAILABLE");
            assertThat(outcome.get("attempts").asInt()).isEqualTo(2);
            assertThat(outcome.get("repairAttempted").asBoolean()).isTrue();
            assertThat(outcome.get("firstFailedRule").asText()).isEqualTo("NO_UNSUPPORTED_FUTURE_PREDICTIONS");
        }
    }

    private void prepare() {
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        metadata();
    }

    private ProfessionalNarrative withMeaning(String whatThisMeans) {
        var safe = narrative();
        return new ProfessionalNarrative(safe.headline(), safe.summary(), whatThisMeans, safe.exposureExplanation(),
                safe.humanAdvantageExplanation(), safe.complementarityExplanation(), safe.adaptabilityExplanation(),
                safe.practicalActions(), safe.cautionOrLimitations());
    }

    @Test
    void historicalV2AndV3NarrativesAreReturnedUnchangedAndNeverRelabelledV4() {
        for (String version : List.of("professional-narrative-v2", "professional-narrative-v3")) {
            org.mockito.Mockito.reset(narrativeStore, assessmentStore, generator);
            StoredProfessionalNarrative historical = available(narrative(), version);
            when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

            StoredProfessionalNarrative result = service().generateFor(ID);

            assertThat(result).isSameAs(historical);
            assertThat(result.promptSchemaVersion()).isEqualTo(version);
            verifyNoInteractions(assessmentStore, generator);
            verify(narrativeStore, never()).save(any());
        }
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
                "Much of the work you described can be performed digitally, which raises your AI Exposure.",
                "Saved human advantage", "Saved complementarity", "Saved adaptability",
                List.of("Review repeatable work", "Strengthen human context", "Document accountability"),
                "This is not a prediction.");
    }

    private StoredProfessionalNarrative available(ProfessionalNarrative value, String version) {
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        return new StoredProfessionalNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(value),
                NarrativeSource.OPENAI, "openai", Optional.of("old-model"), version, time, Optional.of(time));
    }
}
