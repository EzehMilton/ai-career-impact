package com.chikere.futureai.occupation.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfessionalOccupationClassificationServiceTest {
    @Test
    void classifiesSavedAssessmentOnceAndPersistsExactContextAndVersion() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        var assessments = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalOccupationClassificationStore.class);
        var classifier = mock(ProfessionalOccupationClassifier.class);
        var state = new ProfessionalAssessmentState("Registered Nurse", "Provides ward-based care.",
                List.of("Assess patients", "Coordinate care"), List.of("Clinical judgement"), OptionalInt.of(8));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("electrician").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var assessment = new StoredProfessionalAssessment(id, now, state, judgments,
                new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        var classification = new ProfessionalOccupationClassification("Nurses", OccupationClassificationSystem.SOC_2020,
                "2231", OccupationClassificationSource.LOCAL_FIXTURE,
                OccupationClassificationQuality.DEVELOPMENT_FIXTURE, "fixture",
                ProfessionalOccupationClassifierVersions.V1);
        when(store.findByAssessmentId(id)).thenReturn(Optional.empty());
        when(assessments.findById(id)).thenReturn(Optional.of(assessment));
        when(classifier.classify(any())).thenReturn(Optional.of(classification));
        var service = new ProfessionalOccupationClassificationService(assessments, store,
                Optional.of(classifier), "United Kingdom", Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.classifyFor(id)).contains(new StoredProfessionalOccupationClassification(id,
                classification, now));
        verify(classifier).classify(new ProfessionalOccupationClassificationRequest("Registered Nurse",
                List.of("Assess patients", "Coordinate care"), List.of("Clinical judgement"), "United Kingdom"));
        verify(store).save(new StoredProfessionalOccupationClassification(id, classification, now));
        verifyNoMoreInteractions(classifier);
    }

    @Test
    void savedHistoricalClassificationIsNeverReclassified() {
        UUID id = UUID.randomUUID();
        var assessments = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalOccupationClassificationStore.class);
        var classifier = mock(ProfessionalOccupationClassifier.class);
        var stored = new StoredProfessionalOccupationClassification(id,
                new ProfessionalOccupationClassification("Nurses", OccupationClassificationSystem.SOC_2020,
                        "2231", OccupationClassificationSource.LOCAL_FIXTURE,
                        OccupationClassificationQuality.DEVELOPMENT_FIXTURE, "fixture",
                        ProfessionalOccupationClassifierVersions.V1), Instant.parse("2026-01-01T00:00:00Z"));
        when(store.findByAssessmentId(id)).thenReturn(Optional.of(stored));
        var service = new ProfessionalOccupationClassificationService(assessments, store,
                Optional.of(classifier), "United Kingdom", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThat(service.classifyFor(id)).contains(stored);
        verifyNoInteractions(assessments, classifier);
        verify(store, never()).save(any());
    }

    @Test
    void noConfiguredClassifierLeavesClassificationUnavailableWithoutLoadingAssessment() {
        var assessments = mock(ProfessionalAssessmentStore.class);
        var store = mock(ProfessionalOccupationClassificationStore.class);
        var service = new ProfessionalOccupationClassificationService(assessments, store,
                Optional.empty(), "United Kingdom", Clock.systemUTC());
        assertThat(service.classifyFor(UUID.randomUUID())).isEmpty();
        verifyNoInteractions(assessments);
    }
}
