package com.chikere.futureai.narrative.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

class UniversityNarrativeServiceTest {

    private static final UUID ID = UUID.fromString("5f0d1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void generatesOnceFromSavedScoresAndPersistsLifecycleWithProvenance() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var narrativeStore = mock(UniversityNarrativeStore.class);
        var generator = mock(UniversityNarrativeGenerator.class);
        var assessment = snapshot("computer_science");
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(assessment));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        when(generator.modelIdentifier()).thenReturn(Optional.of("gpt-test"));
        doReturn(narrative()).when(generator).generate(any());

        var stored = service(assessmentStore, narrativeStore, generator).generateFor(ID);

        ArgumentCaptor<UniversityNarrativeContext> context =
                ArgumentCaptor.forClass(UniversityNarrativeContext.class);
        verify(generator, times(1)).generate(context.capture());
        // The context carries the saved authoritative values; the generator never recalculates them.
        assertThat(context.getValue().measures()).hasSize(5);
        assertThat(context.getValue().measures().getFirst().measure())
                .isEqualTo(UniversityMeasure.FUTURE_READINESS);
        assertThat(context.getValue().measures().getFirst().value())
                .isEqualTo(assessment.result().decision().measure(UniversityMeasure.FUTURE_READINESS)
                        .orElseThrow().indicator());
        assertThat(context.getValue().courseTitle()).isEqualTo("Computer Science");

        ArgumentCaptor<StoredUniversityNarrative> saved =
                ArgumentCaptor.forClass(StoredUniversityNarrative.class);
        verify(narrativeStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().status()).isEqualTo(NarrativeStatus.PENDING);
        assertThat(stored.status()).isEqualTo(NarrativeStatus.AVAILABLE);
        assertThat(stored.promptSchemaVersion()).isEqualTo("university-narrative-v2");
        assertThat(stored.source()).isEqualTo(NarrativeSource.OPENAI);
        assertThat(stored.modelIdentifier()).contains("gpt-test");
        assertThat(stored.generatedAt()).contains(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void generatorFailureIsRecordedAsFailedAndNeverSelectsAnotherImplementation() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var narrativeStore = mock(UniversityNarrativeStore.class);
        var generator = mock(UniversityNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot("history")));
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
    void invalidNarrativeContentFailsTheAttemptInsteadOfBeingSaved() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var narrativeStore = mock(UniversityNarrativeStore.class);
        var generator = mock(UniversityNarrativeGenerator.class);
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot("economics")));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(generator.source()).thenReturn(NarrativeSource.OPENAI);
        when(generator.providerIdentifier()).thenReturn("openai");
        doReturn(new UniversityNarrative("Headline", "Graduates earn £45,000 on average.", "Meaning",
                "Readiness", "Strengths", "Development", "Complementarity", "Transferability", "Adaptability",
                List.of("One", "Two", "Three"), "Caution")).when(generator).generate(any());

        var stored = service(assessmentStore, narrativeStore, generator).generateFor(ID);

        assertThat(stored.status()).isEqualTo(NarrativeStatus.FAILED);
        assertThat(stored.narrative()).isEmpty();
    }

    @Test
    void existingHistoricalNarrativeIsReturnedWithoutRegenerationOrUpgrade() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var narrativeStore = mock(UniversityNarrativeStore.class);
        var generator = mock(UniversityNarrativeGenerator.class);
        Instant time = Instant.parse("2025-01-01T00:00:00Z");
        var historical = new StoredUniversityNarrative(ID, NarrativeStatus.AVAILABLE,
                Optional.of(narrative()), NarrativeSource.OPENAI, "openai", Optional.of("old-model"),
                "university-narrative-v0", time, Optional.of(time));
        when(narrativeStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

        var service = service(assessmentStore, narrativeStore, generator);

        assertThat(service.generateFor(ID)).isSameAs(historical);
        assertThat(service.findFor(ID)).contains(historical);
        verify(generator, never()).generate(any());
        verify(narrativeStore, never()).save(any());
        verify(assessmentStore, never()).findById(any());
    }

    private UniversityNarrativeService service(UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore, UniversityNarrativeGenerator generator) {
        return new UniversityNarrativeService(assessmentStore, narrativeStore, generator,
                new UniversityNarrativeContextFactory(), new UniversityNarrativeValidator(), CLOCK);
    }

    private StoredUniversityAssessment snapshot(String fixtureId) {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.of("United Kingdom"));
        var judgments = new LocalFixtureUniversityJudgmentProvider(fixtureId).judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T11:59:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private UniversityNarrative narrative() {
        return new UniversityNarrative("A practical reading of this course", "A useful saved summary",
                "This assessment suggests how the course develops capability.",
                "Future Readiness reflects the balance of capabilities described.",
                "This course appears to develop independent judgement.",
                "One area you may want to strengthen is applied practice.",
                "There appears to be scope to work alongside AI tools.",
                "The capabilities may apply beyond one field.",
                "This describes the pathways the course opens, not your circumstances.",
                List.of("Look at how the modules are assessed.",
                        "Build practical experience with relevant AI tools.",
                        "Talk to current students about the course."),
                "This is not official guidance and not a guarantee about employment.");
    }
}
