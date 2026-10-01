package com.chikere.futureai.evidence.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.evidence.alevel.fixture.LocalFixtureALevelEvidenceProvider;
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

class ALevelEvidenceServiceTest {

    private static final UUID ID = UUID.fromString("6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void withNoConfiguredProviderEvidenceStaysNotRequested() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());

        var result = service(assessmentStore, evidenceStore, Optional.empty()).gatherFor(ID);

        assertThat(result).isEmpty();
        verify(evidenceStore, never()).save(any());
        verify(assessmentStore, never()).findById(any());
    }

    @Test
    void persistsPendingThenAvailableAndNeverChangesTheAssessment() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        var assessment = snapshot();
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(assessment));
        var provider = new LocalFixtureALevelEvidenceProvider("maths_economics_geography");

        var result = service(assessmentStore, evidenceStore, Optional.of(provider)).gatherFor(ID);

        ArgumentCaptor<ALevelEvidenceBundle> saved = ArgumentCaptor.forClass(ALevelEvidenceBundle.class);
        verify(evidenceStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(result).get().satisfies(bundle -> {
            assertThat(bundle.status()).isEqualTo(EvidenceStatus.AVAILABLE);
            assertThat(bundle.evidenceVersion()).isEqualTo("a-level-evidence-v1");
            assertThat(bundle.providerIdentifier()).isEqualTo("local-fixture:maths_economics_geography");
        });
        // The authoritative decision is untouched by evidence, including by a prerequisite note.
        ALevelDecisionResult decision = assessment.result().decision();
        assertThat(decision).isEqualTo(new ALevelDecisionPolicyV1().decide(assessment.judgments()));
    }

    @Test
    void providerFailureIsRecordedAsFailedWithoutAnyFallback() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        var provider = mock(ALevelEvidenceProvider.class);
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        when(provider.source()).thenReturn(EvidenceSource.LIVE);
        when(provider.providerIdentifier()).thenReturn("official-provider");
        doThrow(new IllegalStateException("upstream unavailable")).when(provider).gather(any());

        var result = service(assessmentStore, evidenceStore, Optional.of(provider)).gatherFor(ID);

        assertThat(result).get().satisfies(bundle -> {
            assertThat(bundle.status()).isEqualTo(EvidenceStatus.FAILED);
            assertThat(bundle.items()).isEmpty();
            assertThat(bundle.source()).isEqualTo(EvidenceSource.LIVE);
        });
    }

    @Test
    void inconsistentProviderMetadataIsRejectedRatherThanStored() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        var provider = mock(ALevelEvidenceProvider.class);
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        when(provider.source()).thenReturn(EvidenceSource.LIVE);
        when(provider.providerIdentifier()).thenReturn("official-provider");
        doReturn(new ALevelEvidenceBundle(UUID.randomUUID(), "a-level-evidence-v1", EvidenceSource.LIVE,
                "official-provider", EvidenceStatus.AVAILABLE, Instant.parse("2026-10-01T12:00:00Z"),
                new LocalFixtureALevelEvidenceProvider("mixed_balanced").gather(
                        new ALevelEvidenceRequest(ID, List.of("Mathematics"), List.of(), List.of(),
                                "United Kingdom", JourneyType.A_LEVEL)).items()))
                .when(provider).gather(any());

        var result = service(assessmentStore, evidenceStore, Optional.of(provider)).gatherFor(ID);

        assertThat(result).get().extracting(ALevelEvidenceBundle::status).isEqualTo(EvidenceStatus.FAILED);
    }

    @Test
    void existingHistoricalEvidenceIsNeverRefreshed() {
        var assessmentStore = mock(ALevelAssessmentStore.class);
        var evidenceStore = mock(ALevelEvidenceStore.class);
        var provider = mock(ALevelEvidenceProvider.class);
        var historical = new ALevelEvidenceBundle(ID, "a-level-evidence-v0", EvidenceSource.LIVE,
                "old-provider", EvidenceStatus.FAILED, Instant.parse("2025-01-01T00:00:00Z"), List.of());
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

        var service = service(assessmentStore, evidenceStore, Optional.of(provider));

        assertThat(service.gatherFor(ID)).contains(historical);
        assertThat(service.findFor(ID)).contains(historical);
        verify(provider, never()).gather(any());
        verify(evidenceStore, never()).save(any());
    }

    @Test
    void theRequestCarriesSavedSubjectsAndExplicitGeography() {
        var factory = new ALevelEvidenceRequestFactory("United Kingdom");

        ALevelEvidenceRequest request = factory.create(snapshot());

        assertThat(request.assessmentId()).isEqualTo(ID);
        assertThat(request.selectedSubjects()).containsExactly("Mathematics", "Economics", "Geography");
        assertThat(request.preferredDegreeAreas()).containsExactly("Economics");
        assertThat(request.journeyType()).isEqualTo(JourneyType.A_LEVEL);
        // The submitted country wins; otherwise the configured default applies.
        assertThat(request.geography()).isEqualTo("Scotland");
        assertThat(factory.create(snapshot(Optional.empty())).geography()).isEqualTo("United Kingdom");
    }

    private ALevelEvidenceService service(ALevelAssessmentStore assessmentStore,
            ALevelEvidenceStore evidenceStore, Optional<ALevelEvidenceProvider> provider) {
        return new ALevelEvidenceService(assessmentStore, evidenceStore, provider,
                new ALevelEvidenceRequestFactory("United Kingdom"), CLOCK);
    }

    private StoredALevelAssessment snapshot() {
        return snapshot(Optional.of("Scotland"));
    }

    private StoredALevelAssessment snapshot(Optional<String> country) {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of("Economics"), List.of(), country, Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ID, Instant.parse("2026-10-01T11:59:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
