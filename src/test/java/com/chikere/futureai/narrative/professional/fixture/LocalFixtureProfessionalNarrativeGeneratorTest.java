package com.chikere.futureai.narrative.professional.fixture;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContextFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFixtureProfessionalNarrativeGeneratorTest {

    @Test
    void deterministicFixtureUsesOnlySavedContextAndCarriesLocalProvenance() {
        var state = new ProfessionalAssessmentState("Electrician", "Installs and tests electrical systems.",
                List.of("Install wiring", "Test safety"), List.of("Diagnostics"), OptionalInt.of(8));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("electrician").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        var snapshot = new StoredProfessionalAssessment(UUID.randomUUID(), Instant.EPOCH, state,
                judgments, result, Optional.empty());
        var context = new ProfessionalNarrativeContextFactory().create(snapshot);
        var generator = new LocalFixtureProfessionalNarrativeGenerator();

        assertThat(generator.generate(context)).isEqualTo(generator.generate(context));
        assertThat(generator.generate(context).practicalActions()).hasSize(4);
        assertThat(generator.generate(context).summary()).contains("local fixture");
        assertThat(generator.source()).isEqualTo(NarrativeSource.LOCAL_FIXTURE);
        assertThat(generator.providerIdentifier()).isEqualTo("local-fixture");
        assertThat(generator.modelIdentifier()).isEmpty();
    }
}
