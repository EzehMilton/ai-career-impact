package com.chikere.futureai.evidence.university.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.evidence.university.fixture.LocalFixtureUniversityEvidenceProvider;
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

class UniversityEvidenceServiceTest {

    private static final UUID ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void withNoConfiguredProviderEvidenceStaysNotRequested() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());

        var result = service(assessmentStore, evidenceStore, Optional.empty()).gatherFor(ID);

        assertThat(result).isEmpty();
        verify(evidenceStore, never()).save(any());
        verify(assessmentStore, never()).findById(any());
    }

    @Test
    void persistsPendingThenAvailableAndNeverChangesTheAssessment() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        var assessment = snapshot();
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(assessment));
        var provider = new LocalFixtureUniversityEvidenceProvider("history");

        var result = service(assessmentStore, evidenceStore, Optional.of(provider)).gatherFor(ID);

        ArgumentCaptor<UniversityEvidenceBundle> saved =
                ArgumentCaptor.forClass(UniversityEvidenceBundle.class);
        verify(evidenceStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(result).get().satisfies(bundle -> {
            assertThat(bundle.status()).isEqualTo(EvidenceStatus.AVAILABLE);
            assertThat(bundle.evidenceVersion()).isEqualTo("university-evidence-v1");
            assertThat(bundle.providerIdentifier()).isEqualTo("local-fixture:history");
        });
        // The authoritative decision is untouched by evidence.
        UniversityDecisionResult decision = assessment.result().decision();
        assertThat(decision).isEqualTo(new UniversityDecisionPolicyV1().decide(assessment.judgments()));
    }

    @Test
    void providerFailureIsRecordedAsFailedWithoutAnyFallback() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        var provider = mock(UniversityEvidenceProvider.class);
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
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        var provider = mock(UniversityEvidenceProvider.class);
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.empty());
        when(assessmentStore.findById(ID)).thenReturn(Optional.of(snapshot()));
        when(provider.source()).thenReturn(EvidenceSource.LIVE);
        when(provider.providerIdentifier()).thenReturn("official-provider");
        doReturn(new UniversityEvidenceBundle(UUID.randomUUID(), "university-evidence-v1",
                EvidenceSource.LIVE, "official-provider", EvidenceStatus.AVAILABLE,
                Instant.parse("2026-10-01T12:00:00Z"),
                new LocalFixtureUniversityEvidenceProvider("history").gather(
                        new UniversityEvidenceRequest(ID, "A course", List.of(), List.of(), List.of(),
                                Optional.empty(), "United Kingdom", JourneyType.UNIVERSITY)).items()))
                .when(provider).gather(any());

        var result = service(assessmentStore, evidenceStore, Optional.of(provider)).gatherFor(ID);

        assertThat(result).get().extracting(UniversityEvidenceBundle::status)
                .isEqualTo(EvidenceStatus.FAILED);
    }

    @Test
    void existingHistoricalEvidenceIsNeverRefreshed() {
        var assessmentStore = mock(UniversityAssessmentStore.class);
        var evidenceStore = mock(UniversityEvidenceStore.class);
        var provider = mock(UniversityEvidenceProvider.class);
        var historical = new UniversityEvidenceBundle(ID, "university-evidence-v0", EvidenceSource.LIVE,
                "old-provider", EvidenceStatus.FAILED, Instant.parse("2025-01-01T00:00:00Z"), List.of());
        when(evidenceStore.findByAssessmentId(ID)).thenReturn(Optional.of(historical));

        var service = service(assessmentStore, evidenceStore, Optional.of(provider));

        assertThat(service.gatherFor(ID)).contains(historical);
        assertThat(service.findFor(ID)).contains(historical);
        verify(provider, never()).gather(any());
        verify(evidenceStore, never()).save(any());
    }

    @Test
    void theRequestCarriesSavedCourseContextAndExplicitGeography() {
        var factory = new UniversityEvidenceRequestFactory("United Kingdom");

        UniversityEvidenceRequest request = factory.create(snapshot());

        assertThat(request.assessmentId()).isEqualTo(ID);
        assertThat(request.courseTitle()).isEqualTo("Computer Science");
        assertThat(request.keyModules()).containsExactly("Algorithms");
        assertThat(request.preferredCareerAreas()).containsExactly("Software engineering");
        assertThat(request.journeyType()).isEqualTo(JourneyType.UNIVERSITY);
        // The submitted country wins; otherwise the configured default applies.
        assertThat(request.geography()).isEqualTo("Ireland");
        assertThat(factory.create(snapshotWithoutCountry()).geography()).isEqualTo("United Kingdom");
    }

    private UniversityEvidenceService service(UniversityAssessmentStore assessmentStore,
            UniversityEvidenceStore evidenceStore, Optional<UniversityEvidenceProvider> provider) {
        return new UniversityEvidenceService(assessmentStore, evidenceStore, provider,
                new UniversityEvidenceRequestFactory("United Kingdom"), CLOCK);
    }

    private StoredUniversityAssessment snapshot() {
        return snapshot(Optional.of("Ireland"));
    }

    private StoredUniversityAssessment snapshotWithoutCountry() {
        return snapshot(Optional.empty());
    }

    private StoredUniversityAssessment snapshot(Optional<String> country) {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), country);
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T11:59:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
