package com.chikere.futureai.decision.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.StreamSupport;

import static com.chikere.futureai.decision.professional.ProfessionalMaterialityCalibrationCases.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** v2 changes which AI Exposure factors are selected, and nothing else. */
class ProfessionalDecisionPolicyV2Test {

    private static final ProfessionalAssessmentState STATE = new ProfessionalAssessmentState("Software Architect",
            "Designs systems", List.of("Design"), List.of("Architecture"), OptionalInt.of(10));

    private final ProfessionalDecisionPolicyV1 v1 = new ProfessionalDecisionPolicyV1();
    private final ProfessionalDecisionPolicyV2 v2 = new ProfessionalDecisionPolicyV2();

    @Test
    void v2IsANewVersionWithANamedThresholdAndTolerance() {
        assertThat(v2.policyVersion()).isEqualTo("professional-decision-v2").isEqualTo(ProfessionalDecisionPolicyV2.POLICY_VERSION);
        assertThat(v1.policyVersion()).isEqualTo("professional-decision-v1");
        assertThat(ProfessionalDecisionPolicyV2.MATERIALITY_THRESHOLD).isEqualTo(0.015);
        assertThat(ProfessionalDecisionPolicyV2.MATERIALITY_TOLERANCE).isPositive().isLessThan(1.0e-6);
        assertThat(v2.decide(judgments(registeredNurse())).policyVersion()).isEqualTo("professional-decision-v2");
    }

    // ---- The boundary ----

    @Test
    void effectsMathematicallyEqualToTheThresholdAreRetainedOnBothSides() {
        // Trust 1.6 and 2.4 are exactly 0.015 in arithmetic but are represented just below it.
        assertThat(effect(only(TRUST, 1.6), TRUST)).isLessThan(0.015).isCloseTo(0.015, within(1e-15));
        assertThat(effect(only(TRUST, 2.4), TRUST)).isGreaterThan(-0.015).isCloseTo(-0.015, within(1e-15));
        assertThat(drivers(v2.decide(only(TRUST, 1.6)))).containsExactly(TRUST);
        assertThat(protective(v2.decide(only(TRUST, 2.4)))).containsExactly(TRUST);
        // Task 2.24 / 1.76 and the accountability extremes reach the threshold from at or above.
        assertThat(drivers(v2.decide(only(TASK, 2.24)))).containsExactly(TASK);
        assertThat(protective(v2.decide(only(TASK, 1.76)))).containsExactly(TASK);
        assertThat(drivers(v2.decide(only(ACCOUNTABILITY, 0.0)))).containsExactly(ACCOUNTABILITY);
        assertThat(protective(v2.decide(only(ACCOUNTABILITY, 4.0)))).containsExactly(ACCOUNTABILITY);
    }

    @Test
    void effectsJustBelowTheThresholdAreRemovedOnBothSidesButStillCountTowardsTheScore() {
        AssessmentJudgmentSet raising = only(TASK, 4.0 * (0.5 + 0.0149 / 0.25));
        AssessmentJudgmentSet lowering = only(TASK, 4.0 * (0.5 - 0.0149 / 0.25));
        assertThat(effect(raising, TASK)).isCloseTo(0.0149, within(1e-12));
        assertThat(effect(lowering, TASK)).isCloseTo(-0.0149, within(1e-12));

        assertThat(drivers(v2.decide(raising))).isEmpty();
        assertThat(protective(v2.decide(lowering))).isEmpty();
        assertThat(drivers(v1.decide(raising))).containsExactly(TASK);
        assertThat(protective(v1.decide(lowering))).containsExactly(TASK);
        // 5 + 10 x 0.0149: the immaterial factor still moves the score.
        assertThat(exposure(v2.decide(raising))).isCloseTo(5.149, within(1e-9));
        assertThat(exposure(v2.decide(lowering))).isCloseTo(4.851, within(1e-9));
    }

    @Test
    void bothSidesCanBeEmptyWithoutChangingTheScore() {
        AssessmentJudgmentSet small = judgments(raw(Map.of(TASK, 0.01, DIGITAL, 0.012, TRUST, -0.014,
                CONTEXT, -0.009, JUDGEMENT, 0.005)));
        ProfessionalDecisionResult decision = v2.decide(small);

        assertThat(decision.topExposureDrivers()).isEmpty();
        assertThat(decision.topProtectiveFactors()).isEmpty();
        assertThat(decision.assessmentDecision().keyDrivers()).isEmpty();
        assertThat(decision.measures()).isEqualTo(v1.decide(small).measures());
        assertThat(exposure(decision)).isCloseTo(5.04, within(1e-9));
    }

    // ---- Scores, bands, confidence and status are identical ----

    @Test
    void forTheSameJudgmentsOnlyTheSelectedFactorsDiffer() {
        Random random = new Random(20261026L);
        int factorDifferences = 0;
        for (int sample = 0; sample < 2_000; sample++) {
            Map<String, Double> scores = new HashMap<>();
            for (String id : List.of(TASK, DIGITAL, TRUST, PHYSICAL, CONTEXT, JUDGEMENT, ACCOUNTABILITY)) {
                // Half the samples sit near the midpoint, where materiality matters.
                scores.put(id, sample % 2 == 0 ? random.nextDouble() * 4.0 : 2.0 + (random.nextDouble() - 0.5) * 0.6);
            }
            AssessmentJudgmentSet judgments = judgments(scores);
            ProfessionalDecisionResult before = v1.decide(judgments);
            ProfessionalDecisionResult after = v2.decide(judgments);

            assertThat(after.measures()).isEqualTo(before.measures());
            assertThat(after.informationSufficiency()).isEqualTo(before.informationSufficiency());
            assertThat(after.assessmentDecision().overallIndicator()).isEqualTo(before.assessmentDecision().overallIndicator());
            assertThat(after.assessmentDecision().resultLabel()).isEqualTo(before.assessmentDecision().resultLabel());
            assertThat(after.assessmentDecision().confidence()).isEqualTo(before.assessmentDecision().confidence());
            assertThat(after.assessmentDecision().status()).isEqualTo(before.assessmentDecision().status());
            // Ranking is by size, so v2's selection is exactly v1's with the immaterial factors taken out.
            assertThat(after.topExposureDrivers()).isEqualTo(material(before.topExposureDrivers()));
            assertThat(after.topProtectiveFactors()).isEqualTo(material(before.topProtectiveFactors()));
            if (!after.topExposureDrivers().equals(before.topExposureDrivers())
                    || !after.topProtectiveFactors().equals(before.topProtectiveFactors())) {
                factorDifferences++;
            }
        }
        assertThat(factorDifferences).as("the sweep exercises materiality").isPositive();
    }

    // ---- Calibration regressions ----

    @Test
    void registeredNurseLosesBothImmaterialRaisingFactorsAndKeepsItsReducingFactors() {
        AssessmentJudgmentSet judgments = judgments(registeredNurse());
        ProfessionalDecisionResult before = v1.decide(judgments);
        ProfessionalDecisionResult after = v2.decide(judgments);

        assertThat(drivers(before)).containsExactly(TASK, JUDGEMENT);
        assertThat(before.topExposureDrivers()).extracting(ProfessionalDecisionFactor::signedEffect)
                .satisfiesExactly(task -> assertThat(task).isCloseTo(0.009, within(1e-12)),
                        judgement -> assertThat(judgement).isCloseTo(0.003, within(1e-12)));
        assertThat(after.topExposureDrivers()).isEmpty();
        assertThat(after.assessmentDecision().keyDrivers()).isEmpty();
        assertThat(protective(after)).containsExactly(DIGITAL, PHYSICAL, TRUST).isEqualTo(protective(before));
        assertThat(after.measures()).isEqualTo(before.measures());
    }

    @Test
    void javaSoftwareDeveloperKeepsItsMaterialFactorsAndRanksTheSurvivors() {
        AssessmentJudgmentSet judgments = judgments(javaSoftwareDeveloper());
        ProfessionalDecisionResult before = v1.decide(judgments);
        ProfessionalDecisionResult after = v2.decide(judgments);

        assertThat(drivers(before)).containsExactly(DIGITAL, PHYSICAL, TRUST);
        assertThat(protective(before)).containsExactly(CONTEXT, JUDGEMENT, TASK);
        assertThat(drivers(after)).containsExactly(DIGITAL, PHYSICAL);
        assertThat(protective(after)).containsExactly(CONTEXT);
        assertThat(after.topProtectiveFactors().getFirst().signedEffect()).isCloseTo(-0.016, within(1e-12));
        assertThat(after.assessmentDecision().keyDrivers()).containsExactly(DIGITAL, PHYSICAL);
        assertThat(after.assessmentDecision().protectiveFactors()).containsExactly(CONTEXT);
        assertThat(after.measures()).isEqualTo(before.measures());
    }

    @Test
    void managementAccountantKeepsAllThreeDriversWithAnEmptyLoweringSide() {
        AssessmentJudgmentSet judgments = judgments(managementAccountant());
        ProfessionalDecisionResult after = v2.decide(judgments);

        assertThat(drivers(after)).containsExactly(DIGITAL, PHYSICAL, TASK).isEqualTo(drivers(v1.decide(judgments)));
        assertThat(after.topProtectiveFactors()).isEmpty();
        assertThat(after.measures()).isEqualTo(v1.decide(judgments).measures());
    }

    @Test
    void everyRepositoryFixtureKeepsExactlyTheSameSelectedFactors() {
        Map<String, List<List<String>>> expected = Map.of(
                "registered_nurse", List.of(List.of(), List.of(DIGITAL, TRUST, PHYSICAL)),
                "senior_software_architect", List.of(List.of(DIGITAL, PHYSICAL), List.of(TASK, CONTEXT, TRUST)),
                "routine_software_developer", List.of(List.of(DIGITAL, TASK, PHYSICAL), List.of()),
                "electrician", List.of(List.of(), List.of(DIGITAL, PHYSICAL, TASK)));
        assertThat(LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()).containsExactlyInAnyOrderElementsOf(
                expected.keySet());
        expected.forEach((fixture, lists) -> {
            AssessmentJudgmentSet judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(STATE);
            ProfessionalDecisionResult before = v1.decide(judgments);
            ProfessionalDecisionResult after = v2.decide(judgments);
            assertThat(after.topExposureDrivers()).as(fixture).isEqualTo(before.topExposureDrivers());
            assertThat(after.topProtectiveFactors()).as(fixture).isEqualTo(before.topProtectiveFactors());
            assertThat(drivers(after)).as(fixture).isEqualTo(lists.get(0));
            assertThat(protective(after)).as(fixture).isEqualTo(lists.get(1));
            assertThat(after.measures()).as(fixture).isEqualTo(before.measures());
        });
    }

    // ---- Trace ----

    @Test
    void theFactorTraceKeepsEveryCandidateAndExplainsEachMaterialityExclusion() {
        try (TraceLogCapture log = TraceLogCapture.start()) {
            ProfessionalDecisionResult traced = new ProfessionalDecisionPolicyV2(AssessmentTrace.enabled())
                    .decide(judgments(registeredNurse()));
            assertThat(traced).isEqualTo(v2.decide(judgments(registeredNurse())));

            JsonNode payload = log.only(TraceStage.FACTOR_SELECTION).get("payload");
            assertThat(payload.get("policyVersion").asText()).isEqualTo("professional-decision-v2");
            assertThat(payload.get("whatIncreasesAiExposure")).isEmpty();
            List<JsonNode> candidates = StreamSupport.stream(payload.get("candidates").spliterator(), false).toList();
            assertThat(candidates).hasSize(7).allSatisfy(candidate -> {
                assertThat(candidate.get("materialityThreshold").asDouble()).isEqualTo(0.015);
                assertThat(candidate.get("absoluteEffect").asDouble())
                        .isEqualTo(Math.abs(candidate.get("signedEffect").asDouble()));
            });
            JsonNode task = candidate(candidates, TASK);
            assertThat(task.get("material").asBoolean()).isFalse();
            assertThat(task.get("selected").asBoolean()).isFalse();
            assertThat(task.get("rank").isNull()).isTrue();
            assertThat(task.get("reason").asText()).matches(
                    "excluded: absolute signed effect 0\\.00(?:9|89+\\d*|900+\\d*) < materiality threshold 0\\.015");
            assertThat(candidate(candidates, JUDGEMENT).get("reason").asText())
                    .startsWith("excluded: absolute signed effect 0.00").endsWith("< materiality threshold 0.015");
            JsonNode digital = candidate(candidates, DIGITAL);
            assertThat(digital.get("material").asBoolean()).isTrue();
            assertThat(digital.get("selected").asBoolean()).isTrue();
            assertThat(digital.get("rank").asInt()).isEqualTo(1);
            // Context and accountability were not supplied: neutral, so no effect at all.
            assertThat(candidate(candidates, CONTEXT).get("material").asBoolean()).isFalse();
            assertThat(candidate(candidates, CONTEXT).get("reason").asText()).contains("neutral midpoint");
        }
    }

    // ---- Helpers ----

    private static JsonNode candidate(List<JsonNode> candidates, String id) {
        return candidates.stream().filter(node -> node.get("dimensionId").asText().equals(id)).findFirst().orElseThrow();
    }

    private static List<ProfessionalDecisionFactor> material(List<ProfessionalDecisionFactor> factors) {
        return factors.stream().filter(factor -> Math.abs(factor.signedEffect()) >= 0.015 - 1e-9).toList();
    }

    /** Every AI Exposure dimension neutral except one. */
    private static AssessmentJudgmentSet only(String id, double score) {
        Map<String, Double> scores = new HashMap<>(raw(Map.of()));
        scores.put(id, score);
        return judgments(scores);
    }

    private double effect(AssessmentJudgmentSet judgments, String id) {
        return v1.decide(judgments).topExposureDrivers().stream()
                .filter(factor -> factor.dimensionId().equals(id))
                .findFirst()
                .or(() -> v1.decide(judgments).topProtectiveFactors().stream()
                        .filter(factor -> factor.dimensionId().equals(id)).findFirst())
                .orElseThrow().signedEffect();
    }

    private static double exposure(ProfessionalDecisionResult decision) {
        return decision.measures().get(ProfessionalMeasure.AI_EXPOSURE).indicator();
    }

    private static List<String> drivers(ProfessionalDecisionResult decision) {
        return decision.topExposureDrivers().stream().map(ProfessionalDecisionFactor::dimensionId).toList();
    }

    private static List<String> protective(ProfessionalDecisionResult decision) {
        return decision.topProtectiveFactors().stream().map(ProfessionalDecisionFactor::dimensionId).toList();
    }
}
