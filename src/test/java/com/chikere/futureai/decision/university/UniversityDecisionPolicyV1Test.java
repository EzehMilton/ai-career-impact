package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class UniversityDecisionPolicyV1Test {

    private final UniversityDecisionPolicyV1 policy = new UniversityDecisionPolicyV1();

    @Test
    void everyMeasureUsesExplicitWeightsThatTotalExactlyOne() {
        for (UniversityMeasure measure : UniversityMeasure.values()) {
            Map<String, Double> weights = UniversityDecisionPolicyV1.weightsFor(measure);
            assertThat(weights).isNotEmpty();
            assertThat(weights.values().stream().mapToDouble(Double::doubleValue).sum())
                    .as("%s weights", measure).isCloseTo(1.0, within(1.0e-9));
            assertThat(weights.keySet()).allSatisfy(dimension ->
                    assertThat(UniversityDecisionPolicyV1.SCORE_DIMENSIONS).contains(dimension));
        }
        assertThat(UniversityDecisionPolicyV1.explanationWeights().values().stream()
                .mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    void publishedV1WeightsAreTheDocumentedProvisionalPolicy() {
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.FUTURE_READINESS))
                .containsOnly(Map.entry("analytical_reasoning", 0.20),
                        Map.entry("technical_digital_depth", 0.15),
                        Map.entry("original_judgment_creativity", 0.15),
                        Map.entry("ai_complementarity", 0.15),
                        Map.entry("skill_transferability", 0.15),
                        Map.entry("contextual_complexity", 0.10),
                        Map.entry("career_breadth", 0.10));
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.HUMAN_DIFFERENTIATION))
                .containsOnly(Map.entry("original_judgment_creativity", 0.30),
                        Map.entry("human_relationship_dependency", 0.30),
                        Map.entry("contextual_complexity", 0.20),
                        Map.entry("practical_real_world_application", 0.20));
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.AI_COMPLEMENTARITY))
                .containsOnly(Map.entry("ai_complementarity", 0.70),
                        Map.entry("technical_digital_depth", 0.15),
                        Map.entry("analytical_reasoning", 0.15));
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.SKILL_TRANSFERABILITY))
                .containsOnly(Map.entry("skill_transferability", 0.60),
                        Map.entry("analytical_reasoning", 0.20),
                        Map.entry("career_breadth", 0.20));
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.CAREER_ADAPTABILITY))
                .containsOnly(Map.entry("skill_transferability", 0.25),
                        Map.entry("career_breadth", 0.25),
                        Map.entry("analytical_reasoning", 0.20),
                        Map.entry("contextual_complexity", 0.15),
                        Map.entry("ai_complementarity", 0.15));
        assertThat(UniversityDecisionPolicyV1.POLICY_VERSION).isEqualTo("university-decision-v1");
    }

    @Test
    void bandsUseStudentFriendlyThresholds() {
        assertThat(UniversityDecisionPolicyV1.readinessBand(0.0)).isEqualTo(ReadinessBand.LIMITED);
        assertThat(UniversityDecisionPolicyV1.readinessBand(3.49)).isEqualTo(ReadinessBand.LIMITED);
        assertThat(UniversityDecisionPolicyV1.readinessBand(3.50)).isEqualTo(ReadinessBand.DEVELOPING);
        assertThat(UniversityDecisionPolicyV1.readinessBand(6.99)).isEqualTo(ReadinessBand.DEVELOPING);
        assertThat(UniversityDecisionPolicyV1.readinessBand(7.00)).isEqualTo(ReadinessBand.STRONG);
        assertThat(UniversityDecisionPolicyV1.readinessBand(10.0)).isEqualTo(ReadinessBand.STRONG);
        assertThatThrownBy(() -> UniversityDecisionPolicyV1.readinessBand(10.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReadinessBand.values())
                .containsExactly(ReadinessBand.LIMITED, ReadinessBand.DEVELOPING, ReadinessBand.STRONG);
    }

    @Test
    void producesDeterministicContrastingResultsForContrastingCourses() {
        UniversityDecisionResult computing = decide("computer_science");
        UniversityDecisionResult nursing = decide("nursing");
        UniversityDecisionResult history = decide("history");

        assertThat(computing).isEqualTo(decide("computer_science"));

        assertThat(indicator(computing, UniversityMeasure.FUTURE_READINESS)).isCloseTo(8.750, within(0.001));
        assertThat(indicator(computing, UniversityMeasure.AI_COMPLEMENTARITY)).isCloseTo(9.575, within(0.001));
        assertThat(indicator(computing, UniversityMeasure.HUMAN_DIFFERENTIATION)).isCloseTo(6.200, within(0.001));
        assertThat(band(computing, UniversityMeasure.FUTURE_READINESS)).isEqualTo(ReadinessBand.STRONG);
        assertThat(band(computing, UniversityMeasure.HUMAN_DIFFERENTIATION)).isEqualTo(ReadinessBand.DEVELOPING);

        assertThat(indicator(nursing, UniversityMeasure.FUTURE_READINESS)).isCloseTo(6.000, within(0.001));
        assertThat(indicator(nursing, UniversityMeasure.HUMAN_DIFFERENTIATION)).isCloseTo(8.450, within(0.001));
        assertThat(band(nursing, UniversityMeasure.HUMAN_DIFFERENTIATION)).isEqualTo(ReadinessBand.STRONG);

        assertThat(indicator(history, UniversityMeasure.FUTURE_READINESS)).isCloseTo(6.725, within(0.001));
        assertThat(indicator(history, UniversityMeasure.SKILL_TRANSFERABILITY)).isCloseTo(7.400, within(0.001));
        assertThat(indicator(history, UniversityMeasure.AI_COMPLEMENTARITY)).isCloseTo(5.700, within(0.001));

        // AI Complementarity is independent: high readiness does not force high complementarity.
        assertThat(indicator(nursing, UniversityMeasure.AI_COMPLEMENTARITY))
                .isLessThan(indicator(computing, UniversityMeasure.AI_COMPLEMENTARITY));
        assertThat(indicator(nursing, UniversityMeasure.HUMAN_DIFFERENTIATION))
                .isGreaterThan(indicator(computing, UniversityMeasure.HUMAN_DIFFERENTIATION));
    }

    @Test
    void completeDecisionsAlwaysCarryAllFiveMeasuresAndUniversityHeadlineLanguage() {
        UniversityDecisionResult result = decide("computer_science");
        var headline = result.assessmentDecision();

        assertThat(result.measures()).hasSize(5)
                .containsOnlyKeys(UniversityMeasure.values());
        assertThat(headline.journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(headline.resultLabel()).isEqualTo("STRONG FUTURE READINESS");
        assertThat(headline.overallIndicator())
                .hasValue(indicator(result, UniversityMeasure.FUTURE_READINESS));
        assertThat(headline.resultLabel()).doesNotContain("EXPOSURE", "RISK", "AUTOMATION", "SAFE",
                "FUTURE-PROOF", "BAD");
        assertThat(result.policyVersion()).isEqualTo("university-decision-v1");
    }

    @Test
    void informationSufficiencyDrivesStatusAndConfidence() {
        UniversityDecisionResult insufficient = policy.decide(judgments(0.55, 0.85));
        assertThat(insufficient.assessmentDecision().status())
                .isEqualTo(AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(insufficient.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(insufficient.assessmentDecision().overallIndicator()).isEmpty();
        assertThat(insufficient.measures()).isEmpty();
        assertThat(insufficient.topStrengths()).isEmpty();
        assertThat(insufficient.topDevelopmentOpportunities()).isEmpty();

        UniversityDecisionResult caution = policy.decide(judgments(0.70, 0.85));
        assertThat(caution.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(caution.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(caution.measures()).hasSize(5);

        UniversityDecisionResult ready = policy.decide(judgments(0.85, 0.85));
        assertThat(ready.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(ready.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.HIGH);

        // High information but weak judgment confidence must not be reported as a confident result.
        UniversityDecisionResult lowConfidence = policy.decide(judgments(0.95, 0.40));
        assertThat(lowConfidence.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(lowConfidence.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void graphicDesignFixtureExercisesTheCautionPath() {
        UniversityDecisionResult result = decide("graphic_design");

        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(result.measures()).hasSize(5);
        assertThat(band(result, UniversityMeasure.HUMAN_DIFFERENTIATION)).isEqualTo(ReadinessBand.STRONG);
    }

    @Test
    void explainabilityNeverPlacesTheSameDimensionOnBothSides() {
        for (String fixtureId : LocalFixtureUniversityJudgmentProvider.supportedFixtureIds()) {
            UniversityDecisionResult result = decide(fixtureId);
            List<String> strengths = result.topStrengths().stream()
                    .map(UniversityDecisionFactor::dimensionId).toList();
            List<String> opportunities = result.topDevelopmentOpportunities().stream()
                    .map(UniversityDecisionFactor::dimensionId).toList();

            assertThat(strengths).as("%s strengths", fixtureId).doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(3);
            assertThat(opportunities).as("%s opportunities", fixtureId)
                    .doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(3);
            List<String> overlap = strengths.stream().filter(opportunities::contains).toList();
            assertThat(overlap).as("%s overlap", fixtureId).isEmpty();
            assertThat(result.topStrengths()).allSatisfy(factor -> {
                assertThat(factor.effect()).isEqualTo(UniversityFactorEffect.STRENGTH);
                assertThat(factor.signedEffect()).isPositive();
            });
            assertThat(result.topDevelopmentOpportunities()).allSatisfy(factor -> {
                assertThat(factor.effect()).isEqualTo(UniversityFactorEffect.DEVELOPMENT_OPPORTUNITY);
                assertThat(factor.signedEffect()).isNegative();
            });
            assertThat(result.assessmentDecision().keyDrivers()).containsExactlyElementsOf(strengths);
            assertThat(result.assessmentDecision().protectiveFactors()).containsExactlyElementsOf(opportunities);
        }
    }

    @Test
    void explainabilityRanksBySignedContributionAcrossAllNineDimensions() {
        UniversityDecisionResult computing = decide("computer_science");
        UniversityDecisionResult history = decide("history");

        assertThat(computing.topStrengths()).extracting(UniversityDecisionFactor::dimensionId)
                .containsExactly("analytical_reasoning", "technical_digital_depth", "ai_complementarity");
        assertThat(computing.topDevelopmentOpportunities())
                .extracting(UniversityDecisionFactor::dimensionId)
                .containsExactly("human_relationship_dependency");
        assertThat(computing.topDevelopmentOpportunities().getFirst().direction())
                .isEqualTo(UniversityFactorDirection.LOW_HUMAN_RELATIONSHIP_DEPENDENCY);

        // Dimensions outside the Future Readiness weighting can still be explained.
        assertThat(history.topStrengths()).extracting(UniversityDecisionFactor::dimensionId)
                .containsExactly("original_judgment_creativity", "analytical_reasoning", "contextual_complexity");
        assertThat(history.topDevelopmentOpportunities())
                .extracting(UniversityDecisionFactor::dimensionId)
                .containsExactly("technical_digital_depth", "practical_real_world_application");
    }

    @Test
    void rejectsAnythingThatIsNotACompleteUniversityV1JudgmentSet() {
        var valid = new LocalFixtureUniversityJudgmentProvider("economics").judge(state());

        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.PROFESSIONAL, "university-v1", valid.judgments())))
                .isInstanceOf(InvalidUniversityJudgmentSetException.class)
                .hasMessageContaining("journey must be UNIVERSITY");
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.UNIVERSITY, "university-v2", valid.judgments())))
                .isInstanceOf(InvalidUniversityJudgmentSetException.class)
                .hasMessageContaining("schemaVersion must be university-v1");
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.UNIVERSITY, "university-v1", valid.judgments().subList(0, 5))))
                .isInstanceOf(InvalidUniversityJudgmentSetException.class)
                .hasMessageContaining("missing required judgment");

        List<DimensionJudgment> withoutConfidence = new ArrayList<>(valid.judgments());
        DimensionJudgment second = withoutConfidence.get(1);
        withoutConfidence.set(1, new DimensionJudgment(second.dimensionId(), second.name(),
                second.numericValue(), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of()));
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.UNIVERSITY, "university-v1", withoutConfidence)))
                .isInstanceOf(InvalidUniversityJudgmentSetException.class)
                .hasMessageContaining("missing confidence value");
    }

    @Test
    void refusesToConsumeAProfessionalJudgmentSet() {
        var professionalState = new com.chikere.futureai.assessment.domain.ProfessionalAssessmentState(
                "Software Architect", "Designs complex systems", List.of(), List.of(), OptionalInt.empty());
        var professional = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect")
                .judge(professionalState);

        assertThatThrownBy(() -> policy.decide(professional))
                .isInstanceOf(InvalidUniversityJudgmentSetException.class);
        // And the professional policy must equally refuse a university set.
        var university = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state());
        assertThatThrownBy(() -> new ProfessionalDecisionPolicyV1().decide(university))
                .isInstanceOf(RuntimeException.class);
    }

    private UniversityDecisionResult decide(String fixtureId) {
        return policy.decide(new LocalFixtureUniversityJudgmentProvider(fixtureId).judge(state()));
    }

    private double indicator(UniversityDecisionResult result, UniversityMeasure measure) {
        return result.measure(measure).orElseThrow().indicator();
    }

    private ReadinessBand band(UniversityDecisionResult result, UniversityMeasure measure) {
        return result.measure(measure).orElseThrow().band();
    }

    private AssessmentJudgmentSet judgments(double information, double confidence) {
        List<DimensionJudgment> judgments = new ArrayList<>();
        judgments.add(new DimensionJudgment("information_sufficient", "Information sufficiency",
                OptionalDouble.of(information), Optional.empty(), Optional.empty(),
                OptionalDouble.empty(), Map.of()));
        UniversityDecisionPolicyV1.SCORE_DIMENSIONS.forEach(dimension ->
                judgments.add(new DimensionJudgment(dimension, dimension, OptionalDouble.of(2.0),
                        Optional.empty(), Optional.empty(), OptionalDouble.of(confidence), Map.of())));
        return new AssessmentJudgmentSet(JourneyType.UNIVERSITY, "university-v1", judgments);
    }

    private UniversityAssessmentState state() {
        return new UniversityAssessmentState("A course", "A course description with enough detail.",
                List.of("A module"), List.of("A skill"), List.of(), Optional.empty(), Optional.empty());
    }
}
