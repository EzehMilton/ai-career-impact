package com.chikere.futureai.assessment.professional.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfessionalAssessmentStoreTest {

    @MockitoBean ProfessionalJudgmentProvider unusedLiveProvider;
    @Autowired ProfessionalAssessmentStore store;
    @Autowired Flyway flyway;

    @Test
    void migratedPostgresqlRoundTripPreservesCompleteSnapshot() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        var state = new ProfessionalAssessmentState("Software Architect",
                "I design software and make consequential architecture decisions.",
                List.of("Design platforms", "Resolve trade-offs"),
                List.of("System design", "Mentoring"), OptionalInt.of(14));
        var fixture = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var modified = new ArrayList<>(fixture.judgments());
        var score = modified.get(1);
        modified.set(1, new DimensionJudgment(score.dimensionId(), score.name(), score.numericValue(),
                Optional.of("some tasks repeat"), Optional.of(AssessmentConfidence.MODERATE),
                OptionalDouble.of(0.73), Map.of("0", 0.2, "1", 0.3, "2", 0.5)));
        var judgments = new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), modified);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        var snapshot = new StoredProfessionalAssessment(UUID.randomUUID(),
                Instant.parse("2026-09-30T12:00:00Z"), state, judgments, result, Optional.empty());

        store.save(snapshot);
        var loaded = store.findById(snapshot.id()).orElseThrow();

        assertThat(loaded).isEqualTo(snapshot);
        assertThat(loaded.judgments().judgments()).hasSize(9);
        assertThat(loaded.judgments().judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(loaded.judgments().judgments().get(1).probabilityDistribution())
                .containsEntry("1", 0.3);
        assertThat(loaded.result().decision().measures()).hasSize(4);
        assertThat(loaded.result().decision().topExposureDrivers()).isNotEmpty();
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }
}
