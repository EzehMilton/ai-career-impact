package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ALevelNarrativeServiceTest {

    private static final UUID ID = UUID.fromString("4d5e6f7a-8b9c-4d0e-8f1a-2b3c4d5e6f7a");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void generatesOnceFromSavedScoresAndPersistsLifecycleWithProvenance() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        var assessment = snapshot("maths_economics_geography");
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(assessment));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        when(generator.modelIdentifier()).thenReturn(Optional.of("gpt-test"));
        doReturn(narrative()).when(generator).generate(any());

        var stored = service(assessmentStore, narrativeStore, generator).generateFor(ID);

        ArgumentCaptor<ALevelNarrativeContext> context = ArgumentCaptor.forClass(ALevelNarrativeContext.class);
        verify(generator, times(1)).generate(context.capture());
        // The context carries the saved authoritative values; the generator never recalculates them.
        assertThat(context.getValue().measures()).hasSize(6);
        assertThat(context.getValue().measures().getFirst().measure())
                .isEqualTo(ALevelMeasure.FUTURE_OPTIONALITY);
        assertThat(context.getValue().measures().getFirst().value())
                .isEqualTo(assessment.result().decision().measure(ALevelMeasure.FUTURE_OPTIONALITY)
                        .orElseThrow().indicator());
        assertThat(context.getValue().selectedSubjects())
                .containsExactly("Mathematics", "Economics", "Geography");

        ArgumentCaptor<StoredALevelNarrative> saved = ArgumentCaptor.forClass(StoredALevelNarrative.class);
        verify(narrativeStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().status()).isEqualTo(NarrativeStatus.PENDING);
        assertThat(stored.status()).isEqualTo(NarrativeStatus.AVAILABLE);
        assertThat(stored.promptSchemaVersion()).isEqualTo("a-level-narrative-v2");
        assertThat(stored.source()).isEqualTo(NarrativeSource.OPENAI);
        assertThat(stored.modelIdentifier()).contains("gpt-test");
        assertThat(stored.generatedAt()).contains(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void generatorFailureIsRecordedAsFailedAndNeverSelectsAnotherImplementation() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot("mixed_balanced")));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        doThrow(new IllegalStateException("provider exception")).when(generator).generate(any());

        var stored = service(assessmentStore, narrativeStore, generator).generateFor(ID);

        assertThat(stored.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(stored.narrative()).isEmpty();
        assertThat(stored.generatedAt()).isEmpty();
    }

    @Test
    void anInventedPrerequisiteFailsTheAttemptInsteadOfBeingSaved() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot("english_history_politics")));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        doReturn(new ALevelNarrative("Headline", "Medicine requires Chemistry at every university.",
                "Meaning", "Optionality", "Strengths", "Pathways", "Analytical", "Human",
                "Transferability", "Adaptability", "Development", List.of("One", "Two", "Three"),
                "Caution")).when(generator).generate(any());

        var stored = service(assessmentStore, narrativeStore, generator).generateFor(ID);

        assertThat(stored.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(stored.narrative()).isEmpty();
    }

    @Test
    void existingHistoricalNarrativeIsReturnedWithoutRegenerationOrUpgrade() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        var historical = new StoredALevelNarrative(ID, NarrativeStatus.AVAILABLE,
                Optional.of(narrative()), NarrativeSource.OPENAI, "openai", Optional.of("old-model"),
                "a-level-narrative-v0", time, Optional.of(time));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

        var service = service(assessmentStore, narrativeStore, generator);

        assertThat(service.generateFor(ID)).isSameAs(historical);
        assertThat(service.findFor(ID)).contains(historical);
        verify(generator, never()).generate(any());
        verify(narrativeStore, never()).save(any());
        verify(assessmentStore, never()).findById(any());
    }

    @Test
    void aV2SubjectProfileMakesNoOpenAiCallAndStoresNothing() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(
                com.chikere.futureai.assessment.alevel.application.ALevelTestAssessments.subjectProfile(ID,
                        "maths_economics_geography", AssessmentSource.JEV)));

        assertThat(service(assessmentStore, narrativeStore, generator).generateIfApplicable(ID)).isEmpty();

        verify(generator, never()).generate(any());
        verify(narrativeStore, never()).save(any());
    }

    @Test
    void aHistoricalV1AssessmentStillGetsItsFreeNarrativeThroughTheSameEntryPoint() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var narrativeStore = mock(ALevelNarrativeStore.class);
        var generator = mock(ALevelNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot("maths_economics_geography")));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        doReturn(narrative()).when(generator).generate(any());

        assertThat(service(assessmentStore, narrativeStore, generator).generateIfApplicable(ID))
                .hasValueSatisfying(stored -> assertThat(stored.status()).isEqualTo(NarrativeStatus.AVAILABLE));
        verify(generator, times(1)).generate(any());
    }

    private ALevelNarrativeService service(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator) {
        return new ALevelNarrativeService(assessmentStore, narrativeStore, generator,
                new ALevelNarrativeContextFactory(), new ALevelNarrativeValidator(), CLOCK);
    }

    private StoredALevelAssessment snapshot(String fixtureId) {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A"), List.of("Problem solving"), List.of("Economics"),
                List.of(), Optional.of("United Kingdom"), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ID, Instant.parse("2026-10-01T11:59:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private ALevelNarrative narrative() {
        return new ALevelNarrative("A practical reading of your subjects", "A useful saved summary",
                "This assessment suggests how wide a range of next steps these subjects preserve.",
                "Future optionality reflects the balance of breadth and transferable capability.",
                "Your combination appears to build strong structured analysis.",
                "The breadth of degree routes is balanced.",
                "Your analytical foundation covers structured reasoning.",
                "Your human and creative strength covers communication.",
                "Skill transferability describes how widely these capabilities apply.",
                "Adaptability describes the capabilities your subjects build.",
                "One area you may want to research further is technical depth.",
                List.of("Look at how each subject is assessed.",
                        "Build some basic coding and data literacy.",
                        "Talk to your careers adviser about the options."),
                "This is not official guidance and not a statement about where you will end up.");
    }
}
