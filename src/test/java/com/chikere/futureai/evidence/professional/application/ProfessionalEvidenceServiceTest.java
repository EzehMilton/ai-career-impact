package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.*;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.chikere.futureai.occupation.professional.application.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfessionalEvidenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void callsProviderOnceWithNarrowSavedContextAndPersistsItsBundle() {
        UUID id = UUID.randomUUID();
        var assessmentStore = mock(ProfessionalAssessmentStore.class);
        var evidenceStore = mock(ProfessionalEvidenceStore.class);
        var provider = mock(ProfessionalEvidenceProvider.class);
        when(provider.source()).thenReturn(EvidenceSource.LIVE);
        when(provider.providerIdentifier()).thenReturn("real-test-provider");
        when(assessmentStore.findById(id)).thenReturn(Optional.of(snapshot(id)));
        var bundle = available(id, EvidenceSource.LIVE, "real-test-provider");
        when(provider.gather(any())).thenReturn(bundle);
        var service = service(assessmentStore, evidenceStore, Optional.of(provider));

        assertThat(service.gatherFor(id)).contains(bundle);

        ArgumentCaptor<ProfessionalEvidenceRequest> request = ArgumentCaptor.forClass(ProfessionalEvidenceRequest.class);
        verify(provider, times(1)).gather(request.capture());
        assertThat(request.getValue().profession()).isEqualTo("Software Architect");
        assertThat(request.getValue().responsibilities()).containsExactly("Design systems");
        assertThat(request.getValue().skills()).containsExactly("Architecture");
        assertThat(request.getValue().geography()).isEqualTo("United Kingdom");
        assertThat(request.getValue().journeyType()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(request.getValue().occupationClassification()).contains("2133");
        ArgumentCaptor<ProfessionalEvidenceBundle> saved = ArgumentCaptor.forClass(ProfessionalEvidenceBundle.class);
        verify(evidenceStore, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ProfessionalEvidenceBundle::status)
                .containsExactly(EvidenceStatus.PENDING, EvidenceStatus.AVAILABLE);
        verify(assessmentStore, times(1)).findById(id);
    }

    @Test
    void providerFailureStoresFailedWithoutCallingAnyFallback() {
        UUID id = UUID.randomUUID();
        var assessmentStore = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalEvidenceStore.class);
        var real = mock(ProfessionalEvidenceProvider.class);
        var fixture = mock(ProfessionalEvidenceProvider.class);
        when(real.source()).thenReturn(EvidenceSource.LIVE);
        when(real.providerIdentifier()).thenReturn("real");
        when(assessmentStore.findById(id)).thenReturn(Optional.of(snapshot(id)));
        when(real.gather(any())).thenThrow(new IllegalStateException("secret provider response"));

        var result = service(assessmentStore, store, Optional.of(real)).gatherFor(id).orElseThrow();

        assertThat(result.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(result.items()).isEmpty();
        verify(real).gather(any());
        verifyNoInteractions(fixture);
        verify(store, times(2)).save(any());
    }

    @Test
    void historicalBundleIsReturnedWithoutLoadingAssessmentOrCallingProvider() {
        UUID id = UUID.randomUUID();
        var assessmentStore = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalEvidenceStore.class);
        var provider = mock(ProfessionalEvidenceProvider.class);
        var historical = available(id, EvidenceSource.LIVE, "old-provider");
        when(store.findByAssessmentId(id)).thenReturn(Optional.of(historical));

        var result = service(assessmentStore, store, Optional.of(provider)).gatherFor(id);

        assertThat(result).contains(historical);
        assertThat(result.orElseThrow().evidenceVersion()).isEqualTo("professional-evidence-v1");
        verifyNoInteractions(assessmentStore, provider);
        verify(store, never()).save(any());
    }

    @Test
    void absentProfileLeavesEvidenceNotRequestedAndMakesNoProviderCall() {
        var assessmentStore = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalEvidenceStore.class);
        assertThat(service(assessmentStore, store, Optional.empty()).gatherFor(UUID.randomUUID())).isEmpty();
        verifyNoInteractions(assessmentStore);
        verify(store, never()).save(any());
    }

    private ProfessionalEvidenceService service(ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore store, Optional<ProfessionalEvidenceProvider> provider) {
        var classifications = mock(ProfessionalOccupationClassificationStore.class);
        var classification = new ProfessionalOccupationClassification("IT business analysts, architects and systems designers",
                OccupationClassificationSystem.SOC_2020, "2133", OccupationClassificationSource.CONFIGURED_MAPPING,
                OccupationClassificationQuality.EXACT_CONFIGURED_MAPPING, "test", ProfessionalOccupationClassifierVersions.V1);
        when(classifications.findByAssessmentId(any())).thenReturn(Optional.of(
                new StoredProfessionalOccupationClassification(UUID.randomUUID(), classification, NOW)));
        return new ProfessionalEvidenceService(assessmentStore, store, provider,
                classifications, new ProfessionalEvidenceRequestFactory("United Kingdom"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ProfessionalEvidenceBundle available(UUID id, EvidenceSource source, String provider) {
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK,
                "Employment context", "A saved factual summary.", "Official source", Optional.of("ref"),
                Optional.empty(), Optional.of("Publisher"), Optional.of("United Kingdom"), Optional.empty(), NOW,
                Optional.empty(), Optional.empty(), Optional.empty(), EvidenceSourceQuality.OFFICIAL,
                "Related context only.");
        return new ProfessionalEvidenceBundle(id, ProfessionalEvidenceVersions.EVIDENCE_SCHEMA,
                source, provider, EvidenceStatus.AVAILABLE, NOW, List.of(item));
    }

    private StoredProfessionalAssessment snapshot(UUID id) {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs complex systems.",
                List.of("Design systems"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        return new StoredProfessionalAssessment(id, NOW, state, judgments, result, Optional.empty());
    }
}
