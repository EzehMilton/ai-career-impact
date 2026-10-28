package com.chikere.futureai.decision.alevel;

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

import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.AI_LEARNING_SUPPORT;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.ANALYTICAL_REASONING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.CONTEXTUAL_SOCIAL_UNDERSTANDING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.DISCIPLINE_FAMILY_COVERAGE;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.ORIGINAL_JUDGMENT_CREATIVITY;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.QUANTITATIVE_REASONING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.SUBJECT_SKILL_DIVERSITY;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.TECHNICAL_DIGITAL_FOUNDATION;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.WRITTEN_VERBAL_COMMUNICATION;
import static com.chikere.futureai.decision.alevel.ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE;
import static com.chikere.futureai.decision.alevel.ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT;
import static com.chikere.futureai.decision.alevel.ALevelProfileMeasure.LEARNING_ALONGSIDE_AI;
import static com.chikere.futureai.decision.alevel.ALevelProfileMeasure.OPTIONS_KEPT_OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** The production a-level-decision-v2 policy: formulas, bands, factors, confidence and strict input validation. */
public class ALevelDecisionPolicyV2Test {

    private final ALevelDecisionPolicyV2 policy = new ALevelDecisionPolicyV2();

    // ---- formulas ----

    @Test
    void theApprovedWeightsTotalOneAndHaveNoHeadline() {
        assertThat(ALevelDecisionPolicyV2.weightsFor(OPTIONS_KEPT_OPEN)).containsExactly(
                Map.entry(SUBJECT_SKILL_DIVERSITY, 0.40), Map.entry(DISCIPLINE_FAMILY_COVERAGE, 0.60));
        assertThat(ALevelDecisionPolicyV2.weightsFor(ANALYTICAL_AND_QUANTITATIVE)).containsExactly(
                Map.entry(QUANTITATIVE_REASONING, 0.40), Map.entry(ANALYTICAL_REASONING, 0.40),
                Map.entry(TECHNICAL_DIGITAL_FOUNDATION, 0.20));
        assertThat(ALevelDecisionPolicyV2.weightsFor(COMMUNICATION_INTERPRETATION_AND_CONTEXT)).containsExactly(
                Map.entry(WRITTEN_VERBAL_COMMUNICATION, 0.40), Map.entry(ORIGINAL_JUDGMENT_CREATIVITY, 0.30),
                Map.entry(CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.30));
        assertThat(ALevelDecisionPolicyV2.weightsFor(LEARNING_ALONGSIDE_AI)).containsExactly(
                Map.entry(ANALYTICAL_REASONING, 0.15), Map.entry(ORIGINAL_JUDGMENT_CREATIVITY, 0.15),
                Map.entry(AI_LEARNING_SUPPORT, 0.70));
        for (ALevelProfileMeasure measure : ALevelProfileMeasure.values()) {
            assertThat(ALevelDecisionPolicyV2.weightsFor(measure).values().stream().mapToDouble(Double::doubleValue)
                    .sum()).isCloseTo(1.0, within(1e-12));
        }

        var result = policy.decide(judgments(0.88, scores(2.0)));

        assertThat(result.policyVersion()).isEqualTo("a-level-decision-v2");
        assertThat(result.assessmentDecision().overallIndicator()).isEmpty();
        assertThat(result.assessmentDecision().keyDrivers()).isEmpty();
        assertThat(result.measures()).containsOnlyKeys(ALevelProfileMeasure.values());
    }

    @Test
    void eachMeasureIsItsWeightedMeanOnTheTenPointScale() {
        Map<String, Double> scores = scores(2.0);
        scores.put(DISCIPLINE_FAMILY_COVERAGE, 3.4);
        scores.put(SUBJECT_SKILL_DIVERSITY, 1.8);
        scores.put(QUANTITATIVE_REASONING, 4.0);
        scores.put(AI_LEARNING_SUPPORT, 3.0);

        var result = policy.decide(judgments(0.88, scores));

        assertThat(indicator(result, OPTIONS_KEPT_OPEN)).isCloseTo(10 * (0.6 * 3.4 / 4 + 0.4 * 1.8 / 4), within(1e-12));
        assertThat(indicator(result, ANALYTICAL_AND_QUANTITATIVE))
                .isCloseTo(10 * (0.4 * 1.0 + 0.4 * 0.5 + 0.2 * 0.5), within(1e-12));
        assertThat(indicator(result, LEARNING_ALONGSIDE_AI))
                .isCloseTo(10 * (0.7 * 0.75 + 0.15 * 0.5 + 0.15 * 0.5), within(1e-12));
    }

    @Test
    void bandsAreBoundarySafeAtThreePointFiveAndSeven() {
        assertThat(ALevelDecisionPolicyV2.band(3.4999)).isEqualTo(ALevelProfileBand.LOW);
        assertThat(ALevelDecisionPolicyV2.band(3.5)).isEqualTo(ALevelProfileBand.MODERATE);
        assertThat(ALevelDecisionPolicyV2.band(6.9999)).isEqualTo(ALevelProfileBand.MODERATE);
        assertThat(ALevelDecisionPolicyV2.band(7.0)).isEqualTo(ALevelProfileBand.HIGH);
        // A floating-point result a hair below the boundary still counts as the boundary.
        assertThat(ALevelDecisionPolicyV2.band(7.0 - 1e-12)).isEqualTo(ALevelProfileBand.HIGH);
    }

    // ---- factors ----

    @Test
    void driversAndLimitsFollowTheSignedEffectRule() {
        Map<String, Double> scores = scores(2.0);
        scores.put(DISCIPLINE_FAMILY_COVERAGE, 0.8);
        scores.put(SUBJECT_SKILL_DIVERSITY, 1.2);

        var options = policy.decide(judgments(0.88, scores)).measure(OPTIONS_KEPT_OPEN).orElseThrow();

        assertThat(options.band()).isEqualTo(ALevelProfileBand.LOW);
        assertThat(options.limitingFactors()).extracting(ALevelProfileFactor::dimensionId)
                .containsExactly(DISCIPLINE_FAMILY_COVERAGE, SUBJECT_SKILL_DIVERSITY);
        assertThat(options.limitingFactors().getFirst().effect()).isCloseTo(10 * 0.6 * (0.2 - 0.5), within(1e-12));
        assertThat(options.drivers()).isEmpty();
        // Effects sum to the score minus five.
        assertThat(options.contributions().stream().mapToDouble(ALevelProfileFactor::effect).sum())
                .isCloseTo(options.indicator() - 5.0, within(1e-12));
    }

    @Test
    void aCapabilitySideNeverHasLimitsAndNoMeasureHasLimitsInItsTopBand() {
        Map<String, Double> scores = scores(4.0);
        scores.put(QUANTITATIVE_REASONING, 0.0);
        scores.put(ANALYTICAL_REASONING, 0.4);
        scores.put(TECHNICAL_DIGITAL_FOUNDATION, 0.0);
        scores.put(DISCIPLINE_FAMILY_COVERAGE, 4.0);
        scores.put(SUBJECT_SKILL_DIVERSITY, 1.2);

        var result = policy.decide(judgments(0.88, scores));

        var analytical = result.measure(ANALYTICAL_AND_QUANTITATIVE).orElseThrow();
        assertThat(analytical.band()).isEqualTo(ALevelProfileBand.LOW);
        assertThat(analytical.limitingFactors()).isEmpty();
        var options = result.measure(OPTIONS_KEPT_OPEN).orElseThrow();
        assertThat(options.band()).isEqualTo(ALevelProfileBand.HIGH);
        assertThat(options.limitingFactors()).isEmpty();
    }

    @Test
    void factorOrderIsByMagnitudeThenSchemaOrderAndCappedAtTwo() {
        Map<String, Double> scores = scores(4.0);
        var drivers = policy.decide(judgments(0.88, scores)).measure(ANALYTICAL_AND_QUANTITATIVE).orElseThrow()
                .drivers();

        // Quantitative and analytical tie at 0.40; schema order breaks the tie; technical (0.20) is third and cut.
        assertThat(drivers).extracting(ALevelProfileFactor::dimensionId)
                .containsExactly(QUANTITATIVE_REASONING, ANALYTICAL_REASONING);
    }

    @Test
    void immaterialEffectsAreNeverNamed() {
        Map<String, Double> scores = scores(2.0);
        scores.put(DISCIPLINE_FAMILY_COVERAGE, 2.1);

        var options = policy.decide(judgments(0.88, scores)).measure(OPTIONS_KEPT_OPEN).orElseThrow();

        assertThat(options.drivers()).isEmpty();
        assertThat(options.limitingFactors()).isEmpty();
    }

    // ---- information and confidence ----

    @Test
    void insufficientInformationProducesNoMeasures() {
        var result = policy.decide(judgments(0.59, scores(2.0)));

        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(result.measures()).isEmpty();
        assertThat(policy.decide(judgments(0.60, scores(2.0))).measures()).hasSize(4);
    }

    @Test
    void limitedInformationOrTwoLowerConfidenceMeasuresMakeACaution() {
        assertThat(policy.decide(judgments(0.74, scores(2.0))).assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        var ready = policy.decide(judgments(0.80, scores(2.0)));
        assertThat(ready.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(ready.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.HIGH);

        Map<String, Double> confidences = confidences(0.85);
        confidences.put(QUANTITATIVE_REASONING, 0.40);
        confidences.put(ANALYTICAL_REASONING, 0.40);
        var one = policy.decide(judgments(0.88, scores(2.0), confidences));
        assertThat(one.measure(ANALYTICAL_AND_QUANTITATIVE).orElseThrow().lowerConfidence()).isTrue();
        assertThat(one.measure(ANALYTICAL_AND_QUANTITATIVE).orElseThrow().confidence())
                .isCloseTo(0.4 * 0.4 + 0.4 * 0.4 + 0.2 * 0.85, within(1e-12));
        assertThat(one.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(one.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);

        confidences.put(DISCIPLINE_FAMILY_COVERAGE, 0.30);
        confidences.put(SUBJECT_SKILL_DIVERSITY, 0.30);
        assertThat(policy.decide(judgments(0.88, scores(2.0), confidences)).assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
    }

    // ---- strict validation ----

    @Test
    void rejectsMissingExtraDuplicateOrOutOfRangeJudgments() {
        var valid = judgments(0.88, scores(2.0));

        assertThatThrownBy(() -> policy.decide(without(valid, AI_LEARNING_SUPPORT)))
                .isInstanceOf(InvalidALevelJudgmentSetException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> policy.decide(with(valid, score("ai_era_complementarity", 2.0, 0.8))))
                .isInstanceOf(InvalidALevelJudgmentSetException.class).hasMessageContaining("unexpected");
        assertThatThrownBy(() -> policy.decide(with(valid, score(QUANTITATIVE_REASONING, 2.0, 0.8))))
                .isInstanceOf(InvalidALevelJudgmentSetException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> policy.decide(replace(valid, score(QUANTITATIVE_REASONING, 4.5, 0.8))))
                .isInstanceOf(InvalidALevelJudgmentSetException.class).hasMessageContaining("score");
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(JourneyType.A_LEVEL, "a-level-v1",
                valid.judgments()))).isInstanceOf(InvalidALevelJudgmentSetException.class);
    }

    // ---- helpers ----

    private static double indicator(ALevelProfileDecisionResult result, ALevelProfileMeasure measure) {
        return result.measure(measure).orElseThrow().indicator();
    }

    public static Map<String, Double> scores(double value) {
        Map<String, Double> scores = new LinkedHashMap<>();
        ALevelDecisionPolicyV2.SCORE_DIMENSIONS.forEach(id -> scores.put(id, value));
        return scores;
    }

    private static Map<String, Double> confidences(double value) {
        return scores(value);
    }

    public static AssessmentJudgmentSet judgments(double information, Map<String, Double> scores) {
        return judgments(information, scores, confidences(0.85));
    }

    static AssessmentJudgmentSet judgments(double information, Map<String, Double> scores,
            Map<String, Double> confidences) {
        List<DimensionJudgment> judgments = new ArrayList<>();
        judgments.add(new DimensionJudgment("information_sufficient", "Information sufficiency",
                OptionalDouble.of(information), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of()));
        scores.forEach((id, score) -> judgments.add(score(id, score, confidences.get(id))));
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, "a-level-v2", judgments);
    }

    private static DimensionJudgment score(String id, double score, double confidence) {
        return new DimensionJudgment(id, id, OptionalDouble.of(score), Optional.empty(), Optional.empty(),
                OptionalDouble.of(confidence), Map.of());
    }

    private static AssessmentJudgmentSet without(AssessmentJudgmentSet set, String id) {
        return new AssessmentJudgmentSet(set.journey(), set.schemaVersion(),
                set.judgments().stream().filter(judgment -> !judgment.dimensionId().equals(id)).toList());
    }

    private static AssessmentJudgmentSet with(AssessmentJudgmentSet set, DimensionJudgment extra) {
        List<DimensionJudgment> judgments = new ArrayList<>(set.judgments());
        judgments.add(extra);
        return new AssessmentJudgmentSet(set.journey(), set.schemaVersion(), judgments);
    }

    private static AssessmentJudgmentSet replace(AssessmentJudgmentSet set, DimensionJudgment replacement) {
        return with(without(set, replacement.dimensionId()), replacement);
    }
}
