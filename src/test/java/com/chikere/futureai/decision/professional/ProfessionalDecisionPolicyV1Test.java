package com.chikere.futureai.decision.professional;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ProfessionalDecisionPolicyV1Test {

    private static final String INFORMATION_SUFFICIENT = "information_sufficient";
    private static final String TASK_REPEATABILITY = "task_repeatability";
    private static final String DIGITAL_EXECUTABILITY = "digital_executability";
    private static final String HUMAN_TRUST_DEPENDENCY = "human_trust_dependency";
    private static final String PHYSICAL_WORLD_DEPENDENCY = "physical_world_dependency";
    private static final String CONTEXTUAL_COMPLEXITY = "contextual_complexity";
    private static final String ORIGINAL_JUDGMENT = "original_judgment";
    private static final String ACCOUNTABILITY_RESPONSIBILITY = "accountability_responsibility";
    private static final String AI_COMPLEMENTARITY = "ai_complementarity";

    private static final List<String> SCORE_DIMENSIONS = List.of(
            TASK_REPEATABILITY,
            DIGITAL_EXECUTABILITY,
            HUMAN_TRUST_DEPENDENCY,
            PHYSICAL_WORLD_DEPENDENCY,
            CONTEXTUAL_COMPLEXITY,
            ORIGINAL_JUDGMENT,
            ACCOUNTABILITY_RESPONSIBILITY,
            AI_COMPLEMENTARITY);

    private final ProfessionalDecisionPolicyV1 policy = new ProfessionalDecisionPolicyV1();

    @Test
    void highRepeatableDigitalWorkWithFewHumanDependenciesHasHighExposure() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .score(TASK_REPEATABILITY, 4.0)
                .score(DIGITAL_EXECUTABILITY, 4.0)
                .score(HUMAN_TRUST_DEPENDENCY, 0.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 0.0)
                .score(CONTEXTUAL_COMPLEXITY, 0.0)
                .score(ORIGINAL_JUDGMENT, 0.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 0.0)
                .build());

        ProfessionalMeasureResult exposure = measure(result, ProfessionalMeasure.AI_EXPOSURE);
        assertThat(exposure.indicator()).isEqualTo(10.0);
        assertThat(exposure.band()).isEqualTo(IndicatorBand.HIGH);
        assertThat(result.assessmentDecision().overallIndicator()).hasValue(10.0);
        assertThat(result.assessmentDecision().resultLabel()).isEqualTo("HIGH EXPOSURE");
    }

    @Test
    void lowExposureRoleCanHaveStrongHumanAdvantage() {
        ProfessionalDecisionResult result = policy.decide(strongHumanRole().build());

        assertThat(measure(result, ProfessionalMeasure.AI_EXPOSURE).band()).isEqualTo(IndicatorBand.LOW);
        assertThat(measure(result, ProfessionalMeasure.AI_EXPOSURE).indicator()).isEqualTo(0.0);
        assertThat(measure(result, ProfessionalMeasure.HUMAN_ADVANTAGE).band()).isEqualTo(IndicatorBand.HIGH);
        assertThat(measure(result, ProfessionalMeasure.HUMAN_ADVANTAGE).indicator()).isEqualTo(10.0);
    }

    @Test
    void highExposureAndHighComplementarityCanCoexist() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .score(TASK_REPEATABILITY, 4.0)
                .score(DIGITAL_EXECUTABILITY, 4.0)
                .score(HUMAN_TRUST_DEPENDENCY, 0.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 0.0)
                .score(CONTEXTUAL_COMPLEXITY, 0.0)
                .score(ORIGINAL_JUDGMENT, 0.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 0.0)
                .score(AI_COMPLEMENTARITY, 4.0)
                .build());

        assertThat(measure(result, ProfessionalMeasure.AI_EXPOSURE).band()).isEqualTo(IndicatorBand.HIGH);
        assertThat(measure(result, ProfessionalMeasure.AI_COMPLEMENTARITY).band()).isEqualTo(IndicatorBand.HIGH);
    }

    @Test
    void humanAdvantageIsCalculatedIndependentlyRatherThanAsInverseExposure() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .score(TASK_REPEATABILITY, 4.0)
                .score(DIGITAL_EXECUTABILITY, 4.0)
                .score(HUMAN_TRUST_DEPENDENCY, 4.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 4.0)
                .score(CONTEXTUAL_COMPLEXITY, 4.0)
                .score(ORIGINAL_JUDGMENT, 4.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 4.0)
                .build());

        double exposure = measure(result, ProfessionalMeasure.AI_EXPOSURE).indicator();
        double humanAdvantage = measure(result, ProfessionalMeasure.HUMAN_ADVANTAGE).indicator();
        assertThat(exposure).isEqualTo(5.0);
        assertThat(humanAdvantage).isEqualTo(10.0);
        assertThat(humanAdvantage).isNotEqualTo(10.0 - exposure);
    }

    @Test
    void roleAdaptabilityRewardsComplementarityAndDurableHumanCharacteristics() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .score(AI_COMPLEMENTARITY, 4.0)
                .score(CONTEXTUAL_COMPLEXITY, 4.0)
                .score(ORIGINAL_JUDGMENT, 4.0)
                .score(HUMAN_TRUST_DEPENDENCY, 4.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 4.0)
                .score(TASK_REPEATABILITY, 0.0)
                .build());

        ProfessionalMeasureResult adaptability = measure(result, ProfessionalMeasure.ROLE_ADAPTABILITY);
        assertThat(adaptability.indicator()).isEqualTo(10.0);
        assertThat(adaptability.band()).isEqualTo(IndicatorBand.HIGH);
    }

    @Test
    void appliesTheExactExposureWeightsAndExcludesComplementarity() {
        JudgmentSetBuilder judgments = validJudgments()
                .score(TASK_REPEATABILITY, 4.0)
                .score(DIGITAL_EXECUTABILITY, 2.0)
                .score(HUMAN_TRUST_DEPENDENCY, 1.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 3.0)
                .score(CONTEXTUAL_COMPLEXITY, 2.0)
                .score(ORIGINAL_JUDGMENT, 1.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 4.0)
                .score(AI_COMPLEMENTARITY, 0.0);
        double first = measure(policy.decide(judgments.build()), ProfessionalMeasure.AI_EXPOSURE).indicator();

        judgments.score(AI_COMPLEMENTARITY, 4.0);
        double second = measure(policy.decide(judgments.build()), ProfessionalMeasure.AI_EXPOSURE).indicator();

        double expected = 10.0 * (
                0.25 * 1.0
                + 0.25 * 0.5
                + 0.15 * 0.75
                + 0.15 * 0.25
                + 0.10 * 0.5
                + 0.07 * 0.75
                + 0.03 * 0.0);
        assertThat(first).isCloseTo(expected, within(1.0e-12));
        assertThat(second).isCloseTo(expected, within(1.0e-12));
    }

    @Test
    void normalisesAndInvertsTheZeroToFourScale() {
        assertThat(ProfessionalScoreScale.normalise(0.0)).isEqualTo(0.0);
        assertThat(ProfessionalScoreScale.normalise(2.0)).isEqualTo(0.5);
        assertThat(ProfessionalScoreScale.normalise(4.0)).isEqualTo(1.0);
        assertThat(ProfessionalScoreScale.inverse(0.0)).isEqualTo(1.0);
        assertThat(ProfessionalScoreScale.inverse(4.0)).isEqualTo(0.0);
        assertThatIllegalArgumentException().isThrownBy(() -> ProfessionalScoreScale.normalise(4.01));
    }

    @Test
    void bandBoundariesAreCentralisedAndExact() {
        assertThat(ProfessionalDecisionPolicyV1.indicatorBand(3.49)).isEqualTo(IndicatorBand.LOW);
        assertThat(ProfessionalDecisionPolicyV1.indicatorBand(3.50)).isEqualTo(IndicatorBand.MODERATE);
        assertThat(ProfessionalDecisionPolicyV1.indicatorBand(6.99)).isEqualTo(IndicatorBand.MODERATE);
        assertThat(ProfessionalDecisionPolicyV1.indicatorBand(7.00)).isEqualTo(IndicatorBand.HIGH);
    }

    @Test
    void semanticInsufficiencyReturnsBusinessResultWithoutAuthoritativeMeasures() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.59)
                .build());

        assertThat(result.informationSufficiency()).isEqualTo(0.59);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(result.assessmentDecision().overallIndicator()).isEmpty();
        assertThat(result.measures()).isEmpty();
        assertThat(result.topExposureDrivers()).isEmpty();
        assertThat(result.topProtectiveFactors()).isEmpty();
    }

    @Test
    void sufficiencyBoundaryAtPointSixAllowsAssessmentWithCaution() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.60)
                .build());

        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(result.measures()).containsOnlyKeys(ProfessionalMeasure.values());
    }

    @Test
    void sufficiencyBelowPointSevenFiveRequiresCaution() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.74)
                .build());

        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void sufficientInformationWithLowConfidenceStillRequiresCaution() {
        JudgmentSetBuilder judgments = validJudgments().informationSufficiency(0.75);
        SCORE_DIMENSIONS.forEach(id -> judgments.confidence(id, 0.60));

        ProfessionalDecisionResult result = policy.decide(judgments.build());

        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void highSufficiencyAndHighScoreConfidenceAreReady() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.90)
                .build());

        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.HIGH);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void overallConfidenceCanBeModerate() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.70)
                .build());

        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void overallConfidenceCanBeLow() {
        JudgmentSetBuilder judgments = validJudgments().informationSufficiency(0.90);
        SCORE_DIMENSIONS.forEach(id -> judgments.confidence(id, 0.50));

        ProfessionalDecisionResult result = policy.decide(judgments.build());

        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.LOW);
    }

    @Test
    void oneVeryLowDimensionConfidencePreventsHighConfidence() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .informationSufficiency(0.90)
                .confidence(TASK_REPEATABILITY, 0.50)
                .build());

        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    @Test
    void measureConfidenceUsesTheSameWeightsAsItsMeasure() {
        ProfessionalDecisionResult result = policy.decide(validJudgments()
                .confidence(TASK_REPEATABILITY, 0.40)
                .confidence(DIGITAL_EXECUTABILITY, 0.50)
                .confidence(HUMAN_TRUST_DEPENDENCY, 0.60)
                .confidence(PHYSICAL_WORLD_DEPENDENCY, 0.70)
                .confidence(CONTEXTUAL_COMPLEXITY, 0.80)
                .confidence(ORIGINAL_JUDGMENT, 0.90)
                .confidence(ACCOUNTABILITY_RESPONSIBILITY, 1.00)
                .confidence(AI_COMPLEMENTARITY, 0.73)
                .build());

        double expectedExposureConfidence =
                0.25 * 0.40
                + 0.25 * 0.50
                + 0.15 * 0.60
                + 0.15 * 0.70
                + 0.10 * 0.80
                + 0.07 * 0.90
                + 0.03 * 1.00;
        assertThat(measure(result, ProfessionalMeasure.AI_EXPOSURE).confidence())
                .isCloseTo(expectedExposureConfidence, within(1.0e-12));
        assertThat(measure(result, ProfessionalMeasure.AI_COMPLEMENTARITY).confidence())
                .isEqualTo(0.73);
    }

    @Test
    void selectsTopFactorsByWeightedContributionWithDeterministicTieBreaking() {
        ProfessionalDecisionResult exposed = policy.decide(validJudgments()
                .score(TASK_REPEATABILITY, 4.0)
                .score(DIGITAL_EXECUTABILITY, 4.0)
                .score(HUMAN_TRUST_DEPENDENCY, 0.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 0.0)
                .score(CONTEXTUAL_COMPLEXITY, 0.0)
                .score(ORIGINAL_JUDGMENT, 0.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 0.0)
                .build());
        ProfessionalDecisionResult protectedRole = policy.decide(strongHumanRole().build());

        assertThat(exposed.topExposureDrivers())
                .extracting(ProfessionalDecisionFactor::dimensionId)
                .containsExactly(TASK_REPEATABILITY, DIGITAL_EXECUTABILITY, HUMAN_TRUST_DEPENDENCY);
        assertThat(exposed.topExposureDrivers())
                .allSatisfy(factor -> assertThat(factor.effect())
                        .isEqualTo(DecisionFactorEffect.INCREASES_AI_EXPOSURE));
        assertThat(protectedRole.topProtectiveFactors())
                .extracting(ProfessionalDecisionFactor::dimensionId)
                .containsExactly(TASK_REPEATABILITY, DIGITAL_EXECUTABILITY, HUMAN_TRUST_DEPENDENCY);
        assertThat(exposed.topProtectiveFactors()).isEmpty();
        assertThat(protectedRole.topExposureDrivers()).isEmpty();
    }

    @Test
    void liveSoftwareJudgmentsKeepExposureNumbersAndGiveExclusiveDirectionalFactors() {
        ProfessionalDecisionResult routine = policy.decide(validJudgments()
                .informationSufficiency(0.840)
                .score(TASK_REPEATABILITY, 3.580)
                .score(DIGITAL_EXECUTABILITY, 4.000)
                .score(HUMAN_TRUST_DEPENDENCY, 0.500)
                .score(PHYSICAL_WORLD_DEPENDENCY, 0.000)
                .score(CONTEXTUAL_COMPLEXITY, 0.090)
                .score(ORIGINAL_JUDGMENT, 0.110)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 0.500)
                .score(AI_COMPLEMENTARITY, 3.020)
                .build());
        ProfessionalDecisionResult architect = policy.decide(validJudgments()
                .informationSufficiency(0.800)
                .score(TASK_REPEATABILITY, 0.570)
                .score(DIGITAL_EXECUTABILITY, 3.710)
                .score(HUMAN_TRUST_DEPENDENCY, 2.850)
                .score(PHYSICAL_WORLD_DEPENDENCY, 0.020)
                .score(CONTEXTUAL_COMPLEXITY, 3.630)
                .score(ORIGINAL_JUDGMENT, 3.550)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 3.460)
                .score(AI_COMPLEMENTARITY, 3.210)
                .build());

        assertThat(measure(routine, ProfessionalMeasure.AI_EXPOSURE).indicator())
                .isCloseTo(9.471, within(0.001));
        assertThat(measure(architect, ProfessionalMeasure.AI_EXPOSURE).indicator())
                .isCloseTo(4.810, within(0.001));
        assertThat(routine.topProtectiveFactors()).isEmpty();
        assertThat(architect.topExposureDrivers())
                .extracting(ProfessionalDecisionFactor::direction)
                .containsExactly(ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY,
                        ProfessionalFactorDirection.LOW_PHYSICAL_WORLD_DEPENDENCY);
        assertThat(architect.topProtectiveFactors())
                .extracting(ProfessionalDecisionFactor::direction)
                .containsExactly(ProfessionalFactorDirection.LOW_TASK_REPEATABILITY,
                        ProfessionalFactorDirection.HIGH_CONTEXTUAL_COMPLEXITY,
                        ProfessionalFactorDirection.HIGH_HUMAN_TRUST_DEPENDENCY);
        for (ProfessionalDecisionResult result : List.of(routine, architect)) {
            assertThat(result.topExposureDrivers())
                    .allSatisfy(driver -> assertThat(result.topProtectiveFactors())
                            .extracting(ProfessionalDecisionFactor::dimensionId)
                            .doesNotContain(driver.dimensionId()));
        }
    }

    @Test
    void midpointSignedEffectsPreserveDirectionAndExcludeNeutralScores() {
        for (double score : List.of(0.0, 4.0)) {
            ProfessionalDecisionResult result = policy.decide(validJudgments()
                    .score(TASK_REPEATABILITY, score)
                    .score(CONTEXTUAL_COMPLEXITY, score)
                    .build());
            List<ProfessionalDecisionFactor> drivers = result.topExposureDrivers();
            List<ProfessionalDecisionFactor> protectors = result.topProtectiveFactors();
            ProfessionalFactorDirection expectedDriver = score == 4.0
                    ? ProfessionalFactorDirection.HIGH_TASK_REPEATABILITY
                    : ProfessionalFactorDirection.LOW_CONTEXTUAL_COMPLEXITY;
            ProfessionalFactorDirection expectedProtector = score == 4.0
                    ? ProfessionalFactorDirection.HIGH_CONTEXTUAL_COMPLEXITY
                    : ProfessionalFactorDirection.LOW_TASK_REPEATABILITY;
            assertThat(drivers).extracting(ProfessionalDecisionFactor::direction).containsExactly(expectedDriver);
            assertThat(protectors).extracting(ProfessionalDecisionFactor::direction)
                    .containsExactly(expectedProtector);
            assertThat(drivers.getFirst().signedEffect()).isPositive();
            assertThat(protectors.getFirst().signedEffect()).isNegative();
        }
        ProfessionalDecisionResult neutral = policy.decide(validJudgments().build());
        assertThat(neutral.topExposureDrivers()).isEmpty();
        assertThat(neutral.topProtectiveFactors()).isEmpty();
    }

    @Test
    void rejectsWrongJourneyAndSchemaVersion() {
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .journey(JourneyType.UNIVERSITY)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("journey must be PROFESSIONAL");
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .schemaVersion("professional-v2")
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("schemaVersion must be professional-v1");
    }

    @Test
    void rejectsMissingDimensionNumericValueOrConfidence() {
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .omit(ORIGINAL_JUDGMENT)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("missing required judgment: original_judgment");
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .missingScore(TASK_REPEATABILITY)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("missing numeric value for task_repeatability");
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .missingConfidence(DIGITAL_EXECUTABILITY)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("missing confidence value for digital_executability");
    }

    @Test
    void rejectsScoresAndInformationValuesOutsideTheirExpectedRanges() {
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .score(TASK_REPEATABILITY, 4.01)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("task_repeatability score must be between 0.0 and 4.0");
        assertThatThrownBy(() -> policy.decide(validJudgments()
                .informationSufficiency(1.01)
                .build()))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("information_sufficient value must be between 0.0 and 1.0");
    }

    @Test
    void rawConfidenceOutsideZeroToOneFailsAtTheDomainBoundary() {
        assertThatThrownBy(() -> scoreJudgment(TASK_REPEATABILITY, 2.0, OptionalDouble.of(1.01)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceValue must be finite and between 0 and 1");
    }

    @Test
    void rejectsDuplicateDimensions() {
        AssessmentJudgmentSet original = validJudgments().build();
        ArrayList<DimensionJudgment> duplicated = new ArrayList<>(original.judgments());
        duplicated.add(original.judgments().get(1));

        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(
                JourneyType.PROFESSIONAL, "professional-v1", duplicated)))
                .isInstanceOf(InvalidProfessionalJudgmentSetException.class)
                .hasMessageContaining("duplicate judgment");
    }

    @Test
    void producesEqualResultsDeterministicallyAndRecordsPolicyVersion() {
        AssessmentJudgmentSet judgments = validJudgments()
                .score(TASK_REPEATABILITY, 3.2)
                .score(HUMAN_TRUST_DEPENDENCY, 2.7)
                .build();

        ProfessionalDecisionResult first = policy.decide(judgments);
        ProfessionalDecisionResult second = policy.decide(judgments);

        assertThat(first).isEqualTo(second);
        assertThat(first.policyVersion()).isEqualTo("professional-decision-v1");
    }

    private JudgmentSetBuilder strongHumanRole() {
        return validJudgments()
                .score(TASK_REPEATABILITY, 0.0)
                .score(DIGITAL_EXECUTABILITY, 0.0)
                .score(HUMAN_TRUST_DEPENDENCY, 4.0)
                .score(PHYSICAL_WORLD_DEPENDENCY, 4.0)
                .score(CONTEXTUAL_COMPLEXITY, 4.0)
                .score(ORIGINAL_JUDGMENT, 4.0)
                .score(ACCOUNTABILITY_RESPONSIBILITY, 4.0);
    }

    private JudgmentSetBuilder validJudgments() {
        return new JudgmentSetBuilder();
    }

    private ProfessionalMeasureResult measure(
            ProfessionalDecisionResult result,
            ProfessionalMeasure measure) {
        return result.measure(measure).orElseThrow();
    }

    private static DimensionJudgment scoreJudgment(
            String dimensionId,
            double score,
            OptionalDouble confidence) {
        return new DimensionJudgment(
                dimensionId,
                dimensionId,
                OptionalDouble.of(score),
                Optional.empty(),
                Optional.empty(),
                confidence,
                Map.of());
    }

    private static final class JudgmentSetBuilder {

        private JourneyType journey = JourneyType.PROFESSIONAL;
        private String schemaVersion = "professional-v1";
        private double informationSufficiency = 0.90;
        private final Map<String, Double> scores = new LinkedHashMap<>();
        private final Map<String, OptionalDouble> confidences = new LinkedHashMap<>();
        private final List<String> omitted = new ArrayList<>();
        private final List<String> missingScores = new ArrayList<>();

        private JudgmentSetBuilder() {
            SCORE_DIMENSIONS.forEach(id -> {
                scores.put(id, 2.0);
                confidences.put(id, OptionalDouble.of(0.90));
            });
        }

        JudgmentSetBuilder journey(JourneyType value) {
            journey = value;
            return this;
        }

        JudgmentSetBuilder schemaVersion(String value) {
            schemaVersion = value;
            return this;
        }

        JudgmentSetBuilder informationSufficiency(double value) {
            informationSufficiency = value;
            return this;
        }

        JudgmentSetBuilder score(String dimensionId, double value) {
            scores.put(dimensionId, value);
            return this;
        }

        JudgmentSetBuilder confidence(String dimensionId, double value) {
            confidences.put(dimensionId, OptionalDouble.of(value));
            return this;
        }

        JudgmentSetBuilder missingConfidence(String dimensionId) {
            confidences.put(dimensionId, OptionalDouble.empty());
            return this;
        }

        JudgmentSetBuilder omit(String dimensionId) {
            omitted.add(dimensionId);
            return this;
        }

        JudgmentSetBuilder missingScore(String dimensionId) {
            missingScores.add(dimensionId);
            return this;
        }

        AssessmentJudgmentSet build() {
            List<DimensionJudgment> judgments = new ArrayList<>();
            judgments.add(new DimensionJudgment(
                    INFORMATION_SUFFICIENT,
                    "Information sufficiency",
                    OptionalDouble.of(informationSufficiency),
                    Optional.empty(),
                    Optional.empty(),
                    OptionalDouble.empty(),
                    Map.of()));
            SCORE_DIMENSIONS.stream()
                    .filter(id -> !omitted.contains(id))
                    .map(id -> missingScores.contains(id)
                            ? judgmentWithoutNumericValue(id, confidences.get(id))
                            : scoreJudgment(id, scores.get(id), confidences.get(id)))
                    .forEach(judgments::add);
            return new AssessmentJudgmentSet(journey, schemaVersion, judgments);
        }

        private DimensionJudgment judgmentWithoutNumericValue(
                String dimensionId,
                OptionalDouble confidence) {
            return new DimensionJudgment(
                    dimensionId,
                    dimensionId,
                    OptionalDouble.empty(),
                    Optional.of("selected level"),
                    Optional.empty(),
                    confidence,
                    Map.of());
        }
    }
}
