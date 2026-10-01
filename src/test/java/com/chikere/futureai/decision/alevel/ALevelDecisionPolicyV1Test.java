package com.chikere.futureai.decision.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ALevelDecisionPolicyV1Test {

    private final ALevelDecisionPolicyV1 policy = new ALevelDecisionPolicyV1();

    @Test
    void everyMeasureUsesExplicitWeightsThatTotalExactlyOne() {
        for (ALevelMeasure measure : ALevelMeasure.values()) {
            Map<String, Double> weights = ALevelDecisionPolicyV1.weightsFor(measure);
            assertThat(weights).isNotEmpty();
            assertThat(weights.values().stream().mapToDouble(Double::doubleValue).sum())
                    .as("%s weights", measure).isCloseTo(1.0, within(1.0e-9));
            assertThat(weights.keySet()).allSatisfy(dimension ->
                    assertThat(ALevelDecisionPolicyV1.SCORE_DIMENSIONS).contains(dimension));
        }
        Map<String, Double> explanation = ALevelDecisionPolicyV1.explanationWeights();
        assertThat(explanation.values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, within(1.0e-9));
        // Explanation weights must cover every dimension so any capability can be named.
        assertThat(explanation.keySet())
                .containsExactlyInAnyOrderElementsOf(ALevelDecisionPolicyV1.SCORE_DIMENSIONS);
    }

    @Test
    void publishedV1WeightsAreTheDocumentedProvisionalPolicy() {
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.FUTURE_OPTIONALITY))
                .containsOnly(Map.entry("degree_pathway_breadth", 0.250),
                        Map.entry("skill_transferability", 0.200),
                        Map.entry("analytical_reasoning", 0.150),
                        Map.entry("quantitative_reasoning", 0.100),
                        Map.entry("subject_skill_diversity", 0.100),
                        Map.entry("original_judgment_creativity", 0.050),
                        Map.entry("written_verbal_communication", 0.050),
                        Map.entry("contextual_social_understanding", 0.050),
                        Map.entry("technical_digital_foundation", 0.025),
                        Map.entry("ai_era_complementarity", 0.025));
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.DEGREE_PATHWAY_BREADTH))
                .containsOnly(Map.entry("degree_pathway_breadth", 0.75),
                        Map.entry("subject_skill_diversity", 0.25));
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.ANALYTICAL_FOUNDATION))
                .containsOnly(Map.entry("analytical_reasoning", 0.40),
                        Map.entry("quantitative_reasoning", 0.40),
                        Map.entry("technical_digital_foundation", 0.20));
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH))
                .containsOnly(Map.entry("written_verbal_communication", 0.35),
                        Map.entry("original_judgment_creativity", 0.35),
                        Map.entry("contextual_social_understanding", 0.30));
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.SKILL_TRANSFERABILITY))
                .containsOnly(Map.entry("skill_transferability", 0.45),
                        Map.entry("subject_skill_diversity", 0.25),
                        Map.entry("analytical_reasoning", 0.15),
                        Map.entry("written_verbal_communication", 0.15));
        assertThat(ALevelDecisionPolicyV1.weightsFor(ALevelMeasure.AI_ERA_ADAPTABILITY))
                .containsOnly(Map.entry("ai_era_complementarity", 0.35),
                        Map.entry("analytical_reasoning", 0.20),
                        Map.entry("skill_transferability", 0.20),
                        Map.entry("technical_digital_foundation", 0.15),
                        Map.entry("original_judgment_creativity", 0.10));
        assertThat(ALevelDecisionPolicyV1.POLICY_VERSION).isEqualTo("a-level-decision-v1");
    }

    @Test
    void bandsUseMeasuredNonPejorativeLabels() {
        assertThat(ALevelDecisionPolicyV1.optionalityBand(0.0)).isEqualTo(OptionalityBand.FOCUSED);
        assertThat(ALevelDecisionPolicyV1.optionalityBand(3.49)).isEqualTo(OptionalityBand.FOCUSED);
        assertThat(ALevelDecisionPolicyV1.optionalityBand(3.50)).isEqualTo(OptionalityBand.BALANCED);
        assertThat(ALevelDecisionPolicyV1.optionalityBand(6.99)).isEqualTo(OptionalityBand.BALANCED);
        assertThat(ALevelDecisionPolicyV1.optionalityBand(7.00)).isEqualTo(OptionalityBand.BROAD);
        assertThat(ALevelDecisionPolicyV1.optionalityBand(10.0)).isEqualTo(OptionalityBand.BROAD);
        assertThatThrownBy(() -> ALevelDecisionPolicyV1.optionalityBand(10.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(OptionalityBand.values()).extracting(Enum::name)
                .containsExactly("FOCUSED", "BALANCED", "BROAD")
                .doesNotContain("NARROW", "BAD", "WRONG", "SAFE", "FUTURE_PROOF", "WEAK");
    }

    @Test
    void producesDeterministicContrastingResultsForContrastingCombinations() {
        ALevelDecisionResult quantitativeSocial = decide("maths_economics_geography");
        ALevelDecisionResult stem = decide("mathematics_physics_computer_science");
        ALevelDecisionResult humanities = decide("english_history_politics");

        assertThat(quantitativeSocial).isEqualTo(decide("maths_economics_geography"));

        assertThat(indicator(quantitativeSocial, ALevelMeasure.FUTURE_OPTIONALITY))
                .isCloseTo(8.3125, within(0.0001));
        assertThat(indicator(stem, ALevelMeasure.FUTURE_OPTIONALITY)).isCloseTo(7.2625, within(0.0001));
        assertThat(indicator(humanities, ALevelMeasure.FUTURE_OPTIONALITY)).isCloseTo(6.6875, within(0.0001));
        assertThat(indicator(decide("biology_chemistry_mathematics"), ALevelMeasure.FUTURE_OPTIONALITY))
                .isCloseTo(6.9500, within(0.0001));
        assertThat(indicator(decide("art_design_media"), ALevelMeasure.FUTURE_OPTIONALITY))
                .isCloseTo(5.0000, within(0.0001));
        assertThat(indicator(decide("mixed_balanced"), ALevelMeasure.FUTURE_OPTIONALITY))
                .isCloseTo(8.4625, within(0.0001));

        // The supporting measures separate the combinations in opposite directions.
        assertThat(indicator(stem, ALevelMeasure.ANALYTICAL_FOUNDATION)).isCloseTo(9.80, within(0.0001));
        assertThat(indicator(humanities, ALevelMeasure.ANALYTICAL_FOUNDATION)).isCloseTo(4.30, within(0.0001));
        assertThat(indicator(stem, ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH)).isCloseTo(4.40, within(0.0001));
        assertThat(indicator(humanities, ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH))
                .isCloseTo(9.475, within(0.0001));
        assertThat(band(stem, ALevelMeasure.ANALYTICAL_FOUNDATION)).isEqualTo(OptionalityBand.BROAD);
        assertThat(band(humanities, ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH))
                .isEqualTo(OptionalityBand.BROAD);

        // A creative combination is never pushed into the lowest band by the headline measure.
        assertThat(band(decide("art_design_media"), ALevelMeasure.FUTURE_OPTIONALITY))
                .isEqualTo(OptionalityBand.BALANCED);
        assertThat(band(decide("art_design_media"), ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH))
                .isEqualTo(OptionalityBand.BROAD);
    }

    @Test
    void completeDecisionsCarryAllSixMeasuresAndOptionalityHeadlineLanguage() {
        ALevelDecisionResult result = decide("mixed_balanced");
        var headline = result.assessmentDecision();

        assertThat(result.measures()).hasSize(6).containsOnlyKeys(ALevelMeasure.values());
        assertThat(headline.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(headline.resultLabel()).isEqualTo("BROAD FUTURE OPTIONALITY");
        assertThat(headline.overallIndicator()).hasValue(indicator(result, ALevelMeasure.FUTURE_OPTIONALITY));
        assertThat(headline.resultLabel()).doesNotContain("EXPOSURE", "READINESS", "RISK", "AUTOMATION",
                "EMPLOYABILITY", "SAFE", "FUTURE-PROOF");
        assertThat(result.policyVersion()).isEqualTo("a-level-decision-v1");
    }

    @Test
    void informationSufficiencyDrivesStatusAndConfidence() {
        ALevelDecisionResult insufficient = policy.decide(judgments(0.55, 0.85));
        assertThat(insufficient.assessmentDecision().status())
                .isEqualTo(AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(insufficient.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(insufficient.assessmentDecision().overallIndicator()).isEmpty();
        assertThat(insufficient.measures()).isEmpty();
        assertThat(insufficient.topStrengths()).isEmpty();
        assertThat(insufficient.topDevelopmentOpportunities()).isEmpty();

        ALevelDecisionResult caution = policy.decide(judgments(0.70, 0.85));
        assertThat(caution.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(caution.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(caution.measures()).hasSize(6);

        ALevelDecisionResult ready = policy.decide(judgments(0.85, 0.85));
        assertThat(ready.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(ready.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.HIGH);

        // Plenty of information but weak judgment confidence is never reported as confident.
        ALevelDecisionResult lowConfidence = policy.decide(judgments(0.95, 0.40));
        assertThat(lowConfidence.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(lowConfidence.assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void theArtDesignMediaFixtureExercisesTheCautionPath() {
        ALevelDecisionResult result = decide("art_design_media");

        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(result.measures()).hasSize(6);
    }

    @Test
    void explainabilityNeverPlacesTheSameDimensionOnBothSides() {
        for (String fixtureId : LocalFixtureALevelJudgmentProvider.supportedFixtureIds()) {
            ALevelDecisionResult result = decide(fixtureId);
            List<String> strengths = result.topStrengths().stream()
                    .map(ALevelDecisionFactor::dimensionId).toList();
            List<String> opportunities = result.topDevelopmentOpportunities().stream()
                    .map(ALevelDecisionFactor::dimensionId).toList();

            assertThat(strengths).as("%s strengths", fixtureId)
                    .doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(3);
            assertThat(opportunities).as("%s opportunities", fixtureId)
                    .doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(3);
            List<String> overlap = strengths.stream().filter(opportunities::contains).toList();
            assertThat(overlap).as("%s overlap", fixtureId).isEmpty();

            assertThat(result.topStrengths()).allSatisfy(factor -> {
                assertThat(factor.effect()).isEqualTo(ALevelFactorEffect.STRENGTH);
                assertThat(factor.signedEffect()).isPositive();
            });
            assertThat(result.topDevelopmentOpportunities()).allSatisfy(factor -> {
                assertThat(factor.effect()).isEqualTo(ALevelFactorEffect.DEVELOPMENT_OPPORTUNITY);
                assertThat(factor.signedEffect()).isNegative();
            });
            assertThat(result.assessmentDecision().keyDrivers()).containsExactlyElementsOf(strengths);
            assertThat(result.assessmentDecision().protectiveFactors())
                    .containsExactlyElementsOf(opportunities);
        }
    }

    @Test
    void explainabilityRanksBySignedContributionAcrossAllTenDimensions() {
        ALevelDecisionResult quantitativeSocial = decide("maths_economics_geography");
        ALevelDecisionResult stem = decide("mathematics_physics_computer_science");
        ALevelDecisionResult humanities = decide("english_history_politics");

        assertThat(quantitativeSocial.topStrengths()).extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("degree_pathway_breadth", "skill_transferability", "analytical_reasoning");
        assertThat(quantitativeSocial.topDevelopmentOpportunities())
                .extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("technical_digital_foundation");

        assertThat(stem.topStrengths()).extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("analytical_reasoning", "quantitative_reasoning", "skill_transferability");
        assertThat(stem.topDevelopmentOpportunities()).extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("contextual_social_understanding", "written_verbal_communication",
                        "subject_skill_diversity");

        // Capabilities that barely enter Future Optionality can still be named as strengths.
        assertThat(humanities.topStrengths()).extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("written_verbal_communication", "contextual_social_understanding",
                        "analytical_reasoning");
        assertThat(humanities.topDevelopmentOpportunities())
                .extracting(ALevelDecisionFactor::dimensionId)
                .containsExactly("quantitative_reasoning", "technical_digital_foundation");
        assertThat(humanities.topDevelopmentOpportunities().getFirst().direction())
                .isEqualTo(ALevelFactorDirection.LOW_QUANTITATIVE_REASONING);
    }

    @Test
    void rejectsAnythingThatIsNotACompleteALevelV1JudgmentSet() {
        var valid = new LocalFixtureALevelJudgmentProvider("mixed_balanced").judge(state());

        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.UNIVERSITY, "a-level-v1", valid.judgments())))
                .isInstanceOf(InvalidALevelJudgmentSetException.class)
                .hasMessageContaining("journey must be A_LEVEL");
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.A_LEVEL, "a-level-v2", valid.judgments())))
                .isInstanceOf(InvalidALevelJudgmentSetException.class)
                .hasMessageContaining("schemaVersion must be a-level-v1");
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.A_LEVEL, "a-level-v1", valid.judgments().subList(0, 5))))
                .isInstanceOf(InvalidALevelJudgmentSetException.class)
                .hasMessageContaining("missing required judgment");

        List<DimensionJudgment> withoutConfidence = new ArrayList<>(valid.judgments());
        DimensionJudgment second = withoutConfidence.get(1);
        withoutConfidence.set(1, new DimensionJudgment(second.dimensionId(), second.name(),
                second.numericValue(), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of()));
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.A_LEVEL, "a-level-v1", withoutConfidence)))
                .isInstanceOf(InvalidALevelJudgmentSetException.class)
                .hasMessageContaining("missing confidence value");
    }

    @Test
    void refusesToConsumeAnotherJourneysJudgmentSet() {
        var universityState = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of(),
                Optional.empty(), Optional.empty());
        var university = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(universityState);

        assertThatThrownBy(() -> policy.decide(university))
                .isInstanceOf(InvalidALevelJudgmentSetException.class);
        // And the university policy must equally refuse an A-level set.
        var aLevel = new LocalFixtureALevelJudgmentProvider("mixed_balanced").judge(state());
        assertThatThrownBy(() -> new UniversityDecisionPolicyV1().decide(aLevel))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void thePolicyContainsNoSubjectSpecificScoringAtAll() {
        // Two completely different subject lists must give identical results for the same fixture,
        // because the policy scores the judged combination and never the subject names.
        var provider = new LocalFixtureALevelJudgmentProvider("mixed_balanced");
        var mathsHeavy = new ALevelAssessmentState(List.of("Mathematics", "Further Mathematics", "Physics"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var artsHeavy = new ALevelAssessmentState(List.of("Art and Design", "Music", "Drama and Theatre"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());

        assertThat(policy.decide(provider.judge(mathsHeavy)))
                .isEqualTo(policy.decide(provider.judge(artsHeavy)));
    }

    private ALevelDecisionResult decide(String fixtureId) {
        return policy.decide(new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state()));
    }

    private double indicator(ALevelDecisionResult result, ALevelMeasure measure) {
        return result.measure(measure).orElseThrow().indicator();
    }

    private OptionalityBand band(ALevelDecisionResult result, ALevelMeasure measure) {
        return result.measure(measure).orElseThrow().band();
    }

    private AssessmentJudgmentSet judgments(double information, double confidence) {
        List<DimensionJudgment> judgments = new ArrayList<>();
        judgments.add(new DimensionJudgment("information_sufficient", "Information sufficiency",
                OptionalDouble.of(information), Optional.empty(), Optional.empty(),
                OptionalDouble.empty(), Map.of()));
        ALevelDecisionPolicyV1.SCORE_DIMENSIONS.forEach(dimension ->
                judgments.add(new DimensionJudgment(dimension, dimension, OptionalDouble.of(2.0),
                        Optional.empty(), Optional.empty(), OptionalDouble.of(confidence), Map.of())));
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, "a-level-v1", judgments);
    }

    private ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving"), List.of(), List.of(), Optional.empty(), Optional.empty());
    }
}
