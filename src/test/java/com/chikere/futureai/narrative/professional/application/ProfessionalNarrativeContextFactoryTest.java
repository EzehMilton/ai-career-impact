package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalFactorDescriptions;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The narrative input mirrors Java's decision exactly: the factory selects, ranks and scores nothing itself. */
class ProfessionalNarrativeContextFactoryTest {

    @Test
    void everySelectedFactorReachesTheModelInJavasOrderWithItsVisitorFacingDescription() throws Exception {
        for (String fixture : LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()) {
            var state = new ProfessionalAssessmentState("Role", "Work described.", List.of("Responsibility one"),
                    List.of("Skill one"), OptionalInt.empty());
            var judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(state);
            var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
            var assessment = new StoredProfessionalAssessment(UUID.randomUUID(), Instant.parse("2026-10-01T00:00:00Z"),
                    state, judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.JEV,
                            judgments.schemaVersion(), decision.policyVersion()), Optional.empty());

            var context = new ProfessionalNarrativeContextFactory().create(assessment);

            assertMirrors(fixture, context.exposureDrivers(), decision.topExposureDrivers());
            assertMirrors(fixture, context.protectiveFactors(), decision.topProtectiveFactors());
            decision.measures().forEach((measure, result) -> {
                var supplied = context.measures().stream().filter(value -> value.measure() == measure).findFirst()
                        .orElseThrow();
                assertThat(supplied.score()).as(fixture).isEqualTo(result.indicator());
                assertThat(supplied.band()).as(fixture).isEqualTo(
                        result.band().name().charAt(0) + result.band().name().substring(1).toLowerCase());
            });
            assertThat(context.responsibilities()).containsExactly("Responsibility one");
            assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(context))
                    .as("confidence is not supplied to the narrative").doesNotContainIgnoringCase("confidence");
        }
    }

    private static void assertMirrors(String fixture, List<ProfessionalNarrativeContext.FactorContext> supplied,
            List<ProfessionalDecisionFactor> decided) {
        assertThat(supplied).as(fixture).hasSameSizeAs(decided);
        IntStream.range(0, decided.size()).forEach(index -> {
            var factor = decided.get(index);
            var context = supplied.get(index);
            assertThat(context.dimensionId()).isEqualTo(factor.dimensionId());
            assertThat(context.rank()).isEqualTo(index + 1);
            assertThat(context.direction()).isEqualTo(factor.direction());
            assertThat(context.effect()).isEqualTo(factor.effect());
            assertThat(context.judgmentScore()).isEqualTo(factor.score());
            assertThat(context.signedEffect()).isEqualTo(factor.signedEffect());
            assertThat(context.description()).isEqualTo(ProfessionalFactorDescriptions.describe(factor.direction()));
        });
    }
}
