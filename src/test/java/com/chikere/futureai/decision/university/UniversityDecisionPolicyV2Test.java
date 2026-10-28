package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.chikere.futureai.decision.university.UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY;
import static com.chikere.futureai.decision.university.UniversityProfileMeasure.TRANSFERABLE_CAPABILITY;
import static com.chikere.futureai.decision.university.UniversityProfileMeasure.WORKING_ALONGSIDE_AI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

class UniversityDecisionPolicyV2Test {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "university", "baseline-5b");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> DIMENSIONS = UniversityDecisionPolicyV1.SCORE_DIMENSIONS;

    private final UniversityDecisionPolicyV2 policy = new UniversityDecisionPolicyV2();

    // ---- formulas and weights ----

    @Test
    void theApprovedWeightsAreExactAndInSchemaOrder() {
        assertThat(UniversityDecisionPolicyV2.weightsFor(WORKING_ALONGSIDE_AI)).containsExactly(
                entry("technical_digital_depth", 0.15), entry("analytical_reasoning", 0.15),
                entry("contextual_complexity", 0.15), entry("ai_complementarity", 0.55));
        assertThat(UniversityDecisionPolicyV2.weightsFor(HUMAN_AND_PRACTICAL_CAPABILITY)).containsExactly(
                entry("original_judgment_creativity", 0.20), entry("human_relationship_dependency", 0.30),
                entry("contextual_complexity", 0.25), entry("practical_real_world_application", 0.25));
        assertThat(UniversityDecisionPolicyV2.weightsFor(TRANSFERABLE_CAPABILITY)).containsExactly(
                entry("analytical_reasoning", 0.15), entry("contextual_complexity", 0.10),
                entry("skill_transferability", 0.40), entry("career_breadth", 0.35));
        for (UniversityProfileMeasure measure : UniversityProfileMeasure.values()) {
            assertThat(UniversityDecisionPolicyV2.weightsFor(measure).values().stream()
                    .mapToDouble(Double::doubleValue).sum()).as(measure.name()).isCloseTo(1.0, within(1.0e-12));
        }
    }

    @Test
    void invalidWeightsAreRejected() {
        assertThatThrownBy(() -> UniversityDecisionPolicyV2.measureWeights(Map.of("career_breadth", 0.9)))
                .hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> UniversityDecisionPolicyV2.measureWeights(Map.of("salary", 1.0)))
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> UniversityDecisionPolicyV2.measureWeights(Map.of("career_breadth", 1.2,
                "skill_transferability", -0.2))).hasMessageContaining("positive");
    }

    @Test
    void eachFormulaIsTheWeightedMeanOfItsOwnNormalisedJudgments() {
        Map<String, Double> scores = new HashMap<>(Map.of("technical_digital_depth", 1.0, "analytical_reasoning", 2.0,
                "original_judgment_creativity", 3.0, "human_relationship_dependency", 4.0,
                "contextual_complexity", 2.0, "practical_real_world_application", 1.0, "ai_complementarity", 3.0,
                "skill_transferability", 4.0, "career_breadth", 0.0));
        var result = policy.decide(set(0.9, scores, 0.8));

        assertThat(indicator(result, WORKING_ALONGSIDE_AI))
                .isCloseTo(10 * (0.55 * 3 + 0.15 * 1 + 0.15 * 2 + 0.15 * 2) / 4, within(1.0e-9));
        assertThat(indicator(result, HUMAN_AND_PRACTICAL_CAPABILITY))
                .isCloseTo(10 * (0.30 * 4 + 0.25 * 1 + 0.25 * 2 + 0.20 * 3) / 4, within(1.0e-9));
        assertThat(indicator(result, TRANSFERABLE_CAPABILITY))
                .isCloseTo(10 * (0.40 * 4 + 0.35 * 0 + 0.15 * 2 + 0.10 * 2) / 4, within(1.0e-9));
    }

    // ---- bands ----

    @Test
    void bandsChangeAtThreePointFiveAndSeven() {
        assertThat(UniversityDecisionPolicyV2.band(0.0)).isEqualTo(ProfileBand.LOW);
        assertThat(UniversityDecisionPolicyV2.band(3.4999)).isEqualTo(ProfileBand.LOW);
        assertThat(UniversityDecisionPolicyV2.band(3.5)).isEqualTo(ProfileBand.MODERATE);
        assertThat(UniversityDecisionPolicyV2.band(6.9999)).isEqualTo(ProfileBand.MODERATE);
        assertThat(UniversityDecisionPolicyV2.band(7.0)).isEqualTo(ProfileBand.HIGH);
        assertThat(UniversityDecisionPolicyV2.band(10.0)).isEqualTo(ProfileBand.HIGH);
        // A value meant to be exactly on the boundary but a hair under it in floating point keeps the higher band.
        assertThat(UniversityDecisionPolicyV2.band(7.0 - 1.0e-12)).isEqualTo(ProfileBand.HIGH);
        assertThat(UniversityDecisionPolicyV2.band(3.5 - 1.0e-12)).isEqualTo(ProfileBand.MODERATE);
        assertThatThrownBy(() -> UniversityDecisionPolicyV2.band(10.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UniversityDecisionPolicyV2.band(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aScoreExactlyOnSevenIsHigh() {
        // Human and Practical with every dimension at 2.8 is 7.0 (subject to floating point).
        var result = policy.decide(set(0.9, uniform(2.8), 0.8));
        assertThat(result.measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow().band()).isEqualTo(ProfileBand.HIGH);
        assertThat(policy.decide(set(0.9, uniform(1.4), 0.8)).measure(TRANSFERABLE_CAPABILITY).orElseThrow().band())
                .isEqualTo(ProfileBand.MODERATE);
    }

    // ---- factors ----

    @Test
    void effectsSumToTheScoreMinusFiveAndComeOnlyFromTheMeasureFormula() throws IOException {
        for (String course : courses()) {
            var result = policy.decide(fixture(course).judgments());
            for (var measure : result.measures().values()) {
                assertThat(measure.contributions().stream().map(UniversityProfileFactor::dimensionId))
                        .containsExactlyInAnyOrderElementsOf(UniversityDecisionPolicyV2.weightsFor(measure.measure())
                                .keySet());
                double sum = measure.contributions().stream().mapToDouble(UniversityProfileFactor::effect).sum();
                assertThat(sum).as(course + " " + measure.measure()).isCloseTo(measure.indicator() - 5.0,
                        within(1.0e-9));
                for (var factor : measure.contributions()) {
                    assertThat(factor.effect()).isCloseTo(10 * factor.weight() * (factor.score() / 4 - 0.5),
                            within(1.0e-12));
                }
            }
        }
    }

    @Test
    void aFactorIsMaterialFromAQuarterPointIncludingFloatingPointAtTheThreshold() {
        // Practical has weight 0.25: a score of 2.4 is an effect of exactly +0.25 (0.2499999… in floating point).
        Map<String, Double> scores = uniform(2.0);
        scores.put("practical_real_world_application", 2.4);
        var material = policy.decide(set(0.9, scores, 0.8)).measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow();
        assertThat(material.drivers()).extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("practical_real_world_application");

        scores.put("practical_real_world_application", 2.39);
        var immaterial = policy.decide(set(0.9, scores, 0.8)).measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow();
        assertThat(immaterial.drivers()).isEmpty();

        scores.put("practical_real_world_application", 1.6);
        var limiting = policy.decide(set(0.9, scores, 0.8)).measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow();
        assertThat(limiting.limitingFactors()).extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("practical_real_world_application");
        assertThat(limiting.drivers()).isEmpty();
    }

    @Test
    void atMostTwoDriversAndTwoLimitsRankedByEffectSize() {
        Map<String, Double> scores = uniform(2.0);
        scores.put("human_relationship_dependency", 4.0);    // +1.50
        scores.put("practical_real_world_application", 3.6); // +1.00
        scores.put("original_judgment_creativity", 3.6);     // +0.80
        scores.put("contextual_complexity", 3.0);             // +0.625
        var measure = policy.decide(set(0.9, scores, 0.8)).measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow();
        assertThat(measure.drivers()).extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("human_relationship_dependency", "practical_real_world_application");
        assertThat(measure.contributions()).extracting(UniversityProfileFactor::dimensionId).containsExactly(
                "human_relationship_dependency", "practical_real_world_application", "original_judgment_creativity",
                "contextual_complexity");

        Map<String, Double> low = uniform(2.0);
        low.put("skill_transferability", 0.0); // -2.00
        low.put("career_breadth", 0.4);        // -1.40
        low.put("analytical_reasoning", 0.0);  // -0.75
        var limits = policy.decide(set(0.9, low, 0.8)).measure(TRANSFERABLE_CAPABILITY).orElseThrow();
        assertThat(limits.limitingFactors()).extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("skill_transferability", "career_breadth");
    }

    @Test
    void equalEffectsAreOrderedBySchemaOrder() {
        // Contextual complexity and practical application share weight 0.25 in Human and Practical.
        Map<String, Double> scores = uniform(2.0);
        scores.put("practical_real_world_application", 4.0);
        scores.put("contextual_complexity", 4.0);
        var measure = policy.decide(set(0.9, scores, 0.8)).measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow();
        assertThat(measure.drivers()).extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("contextual_complexity", "practical_real_world_application");
    }

    // ---- confidence and status ----

    @Test
    void measureConfidenceIsTheWeightedConfidenceOfItsOwnDimensions() {
        Map<String, Double> confidence = new HashMap<>();
        DIMENSIONS.forEach(dimension -> confidence.put(dimension, 0.9));
        confidence.put("skill_transferability", 0.3);
        confidence.put("career_breadth", 0.5);
        var result = policy.decide(set(0.9, uniform(2.0), confidence));

        var transferable = result.measure(TRANSFERABLE_CAPABILITY).orElseThrow();
        assertThat(transferable.confidence()).isCloseTo(0.40 * 0.3 + 0.35 * 0.5 + 0.15 * 0.9 + 0.10 * 0.9,
                within(1.0e-12));
        assertThat(transferable.lowerConfidence()).isTrue();
        assertThat(result.measure(WORKING_ALONGSIDE_AI).orElseThrow().lowerConfidence()).isFalse();
        // One lower-confidence measure alone does not make the whole profile cautious, but it does lower the
        // internal global confidence: the two deliberately differ.
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
    }

    @Test
    void exactlySixtyPercentConfidenceIsNotLowerConfidence() {
        var result = policy.decide(set(0.9, uniform(2.0), 0.6));
        assertThat(result.measures().values()).allSatisfy(measure -> {
            assertThat(measure.confidence()).isCloseTo(0.6, within(1.0e-12));
            assertThat(measure.lowerConfidence()).isFalse();
        });
        assertThat(policy.decide(set(0.9, uniform(2.0), 0.599)).measures().values())
                .allSatisfy(measure -> assertThat(measure.lowerConfidence()).isTrue());
    }

    @Test
    void twoLowerConfidenceMeasuresMakeTheProfileReadyWithCaution() {
        Map<String, Double> confidence = new HashMap<>();
        DIMENSIONS.forEach(dimension -> confidence.put(dimension, 0.9));
        confidence.put("skill_transferability", 0.2);
        confidence.put("career_breadth", 0.2);
        confidence.put("ai_complementarity", 0.2);
        var result = policy.decide(set(0.95, uniform(2.0), confidence));

        assertThat(result.measures().values().stream().filter(UniversityProfileMeasureResult::lowerConfidence))
                .hasSize(2);
        assertThat(result.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(result.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.MODERATE);
    }

    @Test
    void informationSufficiencyDecidesTheStatusWithoutAnyMeanConfidenceGate() {
        var missing = policy.decide(set(0.5999, uniform(3.0), 0.9));
        assertThat(missing.assessmentDecision().status()).isEqualTo(AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(missing.assessmentDecision().resultLabel()).isEqualTo("MORE INFORMATION REQUIRED");
        assertThat(missing.measures()).isEmpty();

        assertThat(policy.decide(set(0.60, uniform(3.0), 0.9)).assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(policy.decide(set(0.7499, uniform(3.0), 0.9)).assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(policy.decide(set(0.75, uniform(3.0), 0.9)).assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY);
        // v1 would never be HIGH here (mean confidence 0.65 < 0.80); v2 has no such gate.
        var ready = policy.decide(set(0.95, uniform(3.0), 0.65));
        assertThat(ready.assessmentDecision().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(ready.assessmentDecision().confidence()).isEqualTo(AssessmentConfidence.HIGH);
    }

    @Test
    void globalConfidenceIsInternalAndDerivedSeparatelyFromTheStatus() {
        assertThat(policy.decide(set(0.5999, uniform(3.0), 0.9)).assessmentDecision().confidence())
                .isEqualTo(AssessmentConfidence.LOW);
        var thinInformation = policy.decide(set(0.70, uniform(3.0), 0.9)).assessmentDecision();
        assertThat(thinInformation.status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(thinInformation.confidence()).isEqualTo(AssessmentConfidence.MODERATE);
        var full = policy.decide(set(0.75, uniform(3.0), 0.9)).assessmentDecision();
        assertThat(full.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(full.confidence()).isEqualTo(AssessmentConfidence.HIGH);

        // Two lower-confidence measures: both status and confidence fall, through different rules.
        var allLower = policy.decide(set(0.95, uniform(3.0), 0.5)).assessmentDecision();
        assertThat(allLower.status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        assertThat(allLower.confidence()).isEqualTo(AssessmentConfidence.MODERATE);
    }

    @Test
    void aCourseProfileHasNoOverallIndicatorOrHeadlineDrivers() {
        var result = policy.decide(set(0.9, uniform(3.0), 0.9));
        assertThat(result.policyVersion()).isEqualTo("university-decision-v2");
        assertThat(result.assessmentDecision().overallIndicator()).isEmpty();
        assertThat(result.assessmentDecision().resultLabel()).isEqualTo("COURSE PROFILE");
        assertThat(result.assessmentDecision().keyDrivers()).isEmpty();
        assertThat(result.assessmentDecision().protectiveFactors()).isEmpty();
        assertThat(result.measures()).containsOnlyKeys(UniversityProfileMeasure.values());

        var withIndicator = new AssessmentDecision(JourneyType.UNIVERSITY, OptionalDouble.of(5.0), "COURSE PROFILE",
                AssessmentConfidence.HIGH, AssessmentStatus.READY, List.of(), List.of());
        assertThatThrownBy(() -> new UniversityProfileDecisionResult("university-decision-v2", 0.9, withIndicator,
                result.measures())).hasMessageContaining("no overall indicator");
    }

    @Test
    void invalidJudgmentSetsAreRejectedAsInV1() {
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(JourneyType.UNIVERSITY, "university-v2",
                set(0.9, uniform(2.0), 0.8).judgments()))).isInstanceOf(InvalidUniversityJudgmentSetException.class);
        List<DimensionJudgment> missing = new ArrayList<>(set(0.9, uniform(2.0), 0.8).judgments());
        missing.removeIf(judgment -> judgment.dimensionId().equals("career_breadth"));
        assertThatThrownBy(() -> policy.decide(new AssessmentJudgmentSet(JourneyType.UNIVERSITY, "university-v1",
                missing))).hasMessageContaining("career_breadth");
    }

    // ---- frozen Stage 5B fixtures ----

    @Test
    void theSevenFrozenCoursesProduceTheApprovedProfile() throws IOException {
        Map<String, double[]> expected = new LinkedHashMap<>();
        expected.put("computer-science", new double[] {8.91, 5.65, 7.18});
        expected.put("mechanical-engineering", new double[] {9.00, 5.94, 6.27});
        expected.put("graphic-design", new double[] {7.51, 7.97, 4.60});
        expected.put("nursing", new double[] {7.02, 7.67, 4.16});
        expected.put("english-literature", new double[] {6.42, 5.19, 6.78});
        expected.put("law", new double[] {7.28, 5.33, 6.00});
        expected.put("business-management", new double[] {7.30, 5.87, 7.66});
        Map<String, String> bands = Map.of("computer-science", "HIGH MODERATE HIGH",
                "mechanical-engineering", "HIGH MODERATE MODERATE", "graphic-design", "HIGH HIGH MODERATE",
                "nursing", "HIGH HIGH MODERATE", "english-literature", "MODERATE MODERATE MODERATE",
                "law", "HIGH MODERATE MODERATE", "business-management", "HIGH MODERATE HIGH");
        Map<String, List<UniversityProfileMeasure>> lowerConfidence = Map.of(
                "computer-science", List.of(TRANSFERABLE_CAPABILITY),
                "english-literature", List.of(TRANSFERABLE_CAPABILITY),
                "graphic-design", List.of(TRANSFERABLE_CAPABILITY));

        assertThat(courses()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (var course : expected.entrySet()) {
            var result = policy.decide(fixture(course.getKey()).judgments());
            var measures = List.of(WORKING_ALONGSIDE_AI, HUMAN_AND_PRACTICAL_CAPABILITY, TRANSFERABLE_CAPABILITY);
            for (int index = 0; index < 3; index++) {
                assertThat(indicator(result, measures.get(index))).as(course.getKey() + " " + measures.get(index))
                        .isCloseTo(course.getValue()[index], within(0.01));
            }
            assertThat(String.join(" ", measures.stream().map(measure -> result.measure(measure).orElseThrow()
                    .band().name()).toList())).as(course.getKey()).isEqualTo(bands.get(course.getKey()));
            assertThat(result.measures().values().stream().filter(UniversityProfileMeasureResult::lowerConfidence)
                    .map(UniversityProfileMeasureResult::measure).toList()).as(course.getKey())
                    .isEqualTo(lowerConfidence.getOrDefault(course.getKey(), List.of()));
            assertThat(result.assessmentDecision().status()).as(course.getKey()).isEqualTo(AssessmentStatus.READY);
            assertThat(result.assessmentDecision().confidence()).as(course.getKey())
                    .isEqualTo(lowerConfidence.containsKey(course.getKey())
                            ? AssessmentConfidence.MODERATE : AssessmentConfidence.HIGH);
        }
    }

    @Test
    void nursingIsDrivenByItsHumanAndPracticalWorkAndLimitedByPathwayBreadth() throws IOException {
        var result = policy.decide(fixture("nursing").judgments());
        assertThat(result.measure(HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow().drivers())
                .extracting(UniversityProfileFactor::dimensionId)
                .containsExactly("human_relationship_dependency", "practical_real_world_application");
        assertThat(result.measure(TRANSFERABLE_CAPABILITY).orElseThrow().limitingFactors())
                .extracting(UniversityProfileFactor::dimensionId).containsExactly("career_breadth");
    }

    @Test
    void v1StillReproducesEveryStoredV1DecisionExactly() throws IOException {
        assertThat(UniversityDecisionPolicyV1.POLICY_VERSION).isEqualTo("university-decision-v1");
        for (String course : courses()) {
            Fixture fixture = fixture(course);
            var v1 = new UniversityDecisionPolicyV1().decide(fixture.judgments());
            JsonNode stored = fixture.capture().get("decision");
            assertThat(v1.policyVersion()).isEqualTo(stored.get("policyVersion").asText());
            assertThat(v1.assessmentDecision().status().name()).isEqualTo(stored.get("status").asText());
            assertThat(v1.assessmentDecision().confidence().name()).isEqualTo(stored.get("confidence").asText());
            assertThat(v1.assessmentDecision().resultLabel()).isEqualTo(stored.get("headline").asText());
            assertThat(v1.measures()).hasSize(5);
            v1.measures().forEach((measure, result) -> {
                JsonNode value = stored.get("measures").get(measure.name());
                assertThat(result.indicator()).as(course + " " + measure).isEqualTo(value.get("indicator").asDouble());
                assertThat(result.band().name()).isEqualTo(value.get("band").asText());
                assertThat(result.confidence()).isEqualTo(value.get("confidence").asDouble());
            });
            List<String> strengths = new ArrayList<>();
            stored.get("topStrengths").forEach(factor -> strengths.add(factor.get("dimensionId").asText()));
            assertThat(v1.topStrengths()).extracting(UniversityDecisionFactor::dimensionId).isEqualTo(strengths);
        }
    }

    // ---- helpers ----

    private static double indicator(UniversityProfileDecisionResult result, UniversityProfileMeasure measure) {
        return result.measure(measure).orElseThrow().indicator();
    }

    private static Map<String, Double> uniform(double score) {
        Map<String, Double> scores = new HashMap<>();
        DIMENSIONS.forEach(dimension -> scores.put(dimension, score));
        return scores;
    }

    private static AssessmentJudgmentSet set(double information, Map<String, Double> scores, double confidence) {
        Map<String, Double> confidences = new HashMap<>();
        DIMENSIONS.forEach(dimension -> confidences.put(dimension, confidence));
        return set(information, scores, confidences);
    }

    private static AssessmentJudgmentSet set(double information, Map<String, Double> scores,
            Map<String, Double> confidences) {
        List<DimensionJudgment> judgments = new ArrayList<>();
        judgments.add(new DimensionJudgment("information_sufficient", "Information sufficiency",
                OptionalDouble.of(information), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of()));
        for (String dimension : DIMENSIONS) {
            judgments.add(new DimensionJudgment(dimension, dimension, OptionalDouble.of(scores.get(dimension)),
                    Optional.empty(), Optional.empty(), OptionalDouble.of(confidences.get(dimension)), Map.of()));
        }
        return new AssessmentJudgmentSet(JourneyType.UNIVERSITY, "university-v1", judgments);
    }

    private static List<String> courses() throws IOException {
        try (var files = java.nio.file.Files.list(FIXTURES)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5)).sorted().toList();
        }
    }

    private record Fixture(JsonNode capture, AssessmentJudgmentSet judgments) {
    }

    /** A frozen live capture, rebuilt into the judgment set the adapter returned. */
    private static Fixture fixture(String course) throws IOException {
        JsonNode capture = JSON.readTree(FIXTURES.resolve(course + ".json").toFile());
        JsonNode set = capture.get("judgmentSet");
        List<DimensionJudgment> judgments = new ArrayList<>();
        for (JsonNode node : set.get("judgments")) {
            Map<String, Double> probabilities = new LinkedHashMap<>();
            node.get("probabilityDistribution").properties()
                    .forEach(probability -> probabilities.put(probability.getKey(), probability.getValue().asDouble()));
            judgments.add(new DimensionJudgment(node.get("dimensionId").asText(), node.get("name").asText(),
                    OptionalDouble.of(node.get("numericValue").asDouble()),
                    node.get("categoricalValue").isNull() ? Optional.empty()
                            : Optional.of(node.get("categoricalValue").asText()),
                    Optional.empty(),
                    node.get("confidenceValue").isNull() ? OptionalDouble.empty()
                            : OptionalDouble.of(node.get("confidenceValue").asDouble()),
                    probabilities));
        }
        return new Fixture(capture, new AssessmentJudgmentSet(JourneyType.valueOf(set.get("journey").asText()),
                set.get("schemaVersion").asText(), judgments));
    }
}
