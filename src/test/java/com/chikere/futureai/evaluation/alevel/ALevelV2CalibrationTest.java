package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Judgment;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Measure;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Status;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.AI_LEARNING_SUPPORT;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.ANALYTICAL_REASONING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.CONTEXTUAL_SOCIAL_UNDERSTANDING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.DISCIPLINE_FAMILY_COVERAGE;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.ORIGINAL_JUDGMENT_CREATIVITY;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.QUANTITATIVE_REASONING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.SUBJECT_SKILL_DIVERSITY;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.TECHNICAL_DIGITAL_FOUNDATION;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.WRITTEN_VERBAL_COMMUNICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The Stage A1/A2 design rules for a-level-v2 and a-level-decision-v2, offline. No provider is called. */
class ALevelV2CalibrationTest {

    private final ALevelV2CandidatePolicy policy = new ALevelV2CandidatePolicy();

    // ---- schema and input separation ----

    @Test
    void theV2SchemaHasNinePrimitiveScoresAndV1IsUnchanged() {
        var v2 = new ALevelV2CandidateSchema();
        assertThat(v2.version()).isEqualTo("a-level-v2");
        assertThat(v2.questions().keySet()).containsExactly("information_sufficient", "quantitative_reasoning",
                "analytical_reasoning", "written_verbal_communication", "original_judgment_creativity",
                "contextual_social_understanding", "technical_digital_foundation", "subject_skill_diversity",
                "discipline_family_coverage", "ai_learning_support");
        assertThat(v2.questions().keySet()).doesNotContain("degree_pathway_breadth", "skill_transferability");

        var v1 = new ALevelJudgmentSchema();
        assertThat(v1.version()).isEqualTo("a-level-v1");
        assertThat(v1.questions().keySet()).contains("degree_pathway_breadth", "skill_transferability").hasSize(11);
        assertThat(ALevelDecisionPolicyV1.POLICY_VERSION).isEqualTo("a-level-decision-v1");
    }

    @Test
    void aV2JudgmentRequestCarriesOnlyTheSubjects() {
        ALevelAssessmentState rich = ALevelV2CalibrationCases.ALL.get(1).state();
        assertThat(rich.predictedGrades()).isNotEmpty();
        assertThat(rich.preferredCareerAreas()).isNotEmpty();

        var input = ALevelV2JudgmentInput.map(rich);

        assertThat(input).containsOnlyKeys("selected_subjects")
                .containsEntry("selected_subjects", List.of("Mathematics", "Economics", "Geography"));
        assertThat(input.toString()).doesNotContain("Finance", "Environmental consulting", "Explaining ideas",
                "Data-related courses", "United Kingdom", "considering Computer Science");
    }

    @Test
    void guidanceOnlyContextCanNeverChangeTheJudgmentInput() {
        var bare = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(), List.of(),
                List.of(), List.of(), Optional.empty(), Optional.empty());

        assertThat(ALevelV2JudgmentInput.map(ALevelV2CalibrationCases.ALL.get(1).state()))
                .isEqualTo(ALevelV2JudgmentInput.map(bare));
    }

    // ---- measures ----

    @Test
    void thereIsNoHeadlineAndEveryMeasureIsAnExplicitWeightedComposite() {
        var decision = policy.decide(0.88, uniform(2.0, 2.0));

        assertThat(decision.overallIndicator()).isEmpty();
        assertThat(decision.measures()).containsOnlyKeys(Measure.values());
        for (Measure measure : Measure.values()) {
            assertThat(measure.weights.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(1e-12));
        }
        assertThat(Measure.LEARNING_ALONGSIDE_AI.weights).containsEntry(AI_LEARNING_SUPPORT, 0.70);
        // Technical work is part of one capability side only; it carries no weight in breadth or AI learning.
        assertThat(Measure.OPTIONS_KEPT_OPEN.weights).doesNotContainKey(TECHNICAL_DIGITAL_FOUNDATION);
        assertThat(Measure.LEARNING_ALONGSIDE_AI.weights).doesNotContainKey(TECHNICAL_DIGITAL_FOUNDATION);
    }

    @Test
    void thePolicyIsSymmetricBetweenTheTwoCapabilitySides() {
        // An analytical-leaning profile and its mirror image, everything else equal.
        var analytical = policy.decide(0.88, uniform(3.6, 1.2));
        var communicative = policy.decide(0.88, uniform(1.2, 3.6));

        assertThat(indicator(analytical, Measure.OPTIONS_KEPT_OPEN))
                .isEqualTo(indicator(communicative, Measure.OPTIONS_KEPT_OPEN));
        assertThat(indicator(analytical, Measure.LEARNING_ALONGSIDE_AI))
                .isCloseTo(indicator(communicative, Measure.LEARNING_ALONGSIDE_AI), within(1e-12));
        assertThat(indicator(analytical, Measure.ANALYTICAL_AND_QUANTITATIVE))
                .isCloseTo(indicator(communicative, Measure.COMMUNICATION_INTERPRETATION_AND_CONTEXT), within(1e-12));
    }

    @Test
    void aLighterCapabilitySideIsAProfileShapeNeverALimit() {
        var decision = policy.decide(0.88, uniform(0.4, 4.0));

        var lighter = decision.measures().get(Measure.ANALYTICAL_AND_QUANTITATIVE);
        assertThat(lighter.bandLabel()).isEqualTo("Lighter emphasis");
        assertThat(lighter.limits()).isEmpty();
        assertThat(decision.measures().get(Measure.COMMUNICATION_INTERPRETATION_AND_CONTEXT).bandLabel())
                .isEqualTo("Strong emphasis");
    }

    @Test
    void factorPhrasesAreNeutralAndNeverCompareWithOthers() {
        for (Measure measure : Measure.values()) {
            for (String dimension : measure.weights.keySet()) {
                for (boolean driver : List.of(true, false)) {
                    String phrase = ALevelV2CandidatePolicy.phrase(measure,
                            new ALevelV2CandidatePolicy.Factor(dimension, 2.0, 0.1, 0.0), driver);
                    assertThat(phrase.toLowerCase()).doesNotContain(" than ", "weak", "poor", "bad", "other students",
                            "alternatives", "lack");
                }
            }
        }
    }

    @Test
    void thePolicyNamesNoSubject() throws Exception {
        String source = Files.readString(Path.of("src", "test", "java", "com", "chikere", "futureai", "evaluation",
                "alevel", "ALevelV2CandidatePolicy.java"));

        assertThat(source).doesNotContain("Mathematics", "Physics", "Chemistry", "Biology", "Economics", "History",
                "Literature", "Politics", "Psychology", "Sociology", "Geography", "Computer Science", "Art and Design",
                "Business");
    }

    // ---- bands, presentation, confidence and information ----

    @Test
    void scoresAreShownBoundarySafe() {
        assertThat(ALevelV2CandidatePolicy.band(6.996)).isEqualTo(1);
        assertThat(ALevelV2CandidatePolicy.shownScore(6.996, 1)).isEqualTo("6.99");
        assertThat(ALevelV2CandidatePolicy.shownScore(3.4999, 0)).isEqualTo("3.49");
        assertThat(ALevelV2CandidatePolicy.band(7.0)).isEqualTo(2);
        assertThat(ALevelV2CandidatePolicy.shownScore(7.0, 2)).isEqualTo("7.00");
        assertThat(ALevelV2CandidatePolicy.band(3.5)).isEqualTo(1);
    }

    @Test
    void insufficientInformationProducesNoMeasuresAndLowInformationIsACaution() {
        assertThat(policy.decide(0.59, uniform(2.0, 2.0))).satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(Status.MORE_INFORMATION_REQUIRED);
            assertThat(decision.measures()).isEmpty();
        });
        assertThat(policy.decide(0.72, uniform(2.0, 2.0)).status()).isEqualTo(Status.READY_WITH_CAUTION);
        assertThat(policy.decide(0.80, uniform(2.0, 2.0)).status()).isEqualTo(Status.READY);
    }

    @Test
    void lowerConfidenceIsPerMeasureAndTwoOfThemMakeACaution() {
        Map<String, Judgment> judgments = new LinkedHashMap<>(uniform(2.0, 2.0));
        judgments.put(QUANTITATIVE_REASONING, new Judgment(2.0, 0.40));
        judgments.put(ANALYTICAL_REASONING, new Judgment(2.0, 0.40));

        var decision = policy.decide(0.88, judgments);

        assertThat(decision.measures().get(Measure.ANALYTICAL_AND_QUANTITATIVE).lowerConfidence()).isTrue();
        assertThat(decision.measures().get(Measure.COMMUNICATION_INTERPRETATION_AND_CONTEXT).lowerConfidence())
                .isFalse();
        assertThat(decision.status()).isEqualTo(Status.READY);

        judgments.put(DISCIPLINE_FAMILY_COVERAGE, new Judgment(2.0, 0.30));
        judgments.put(SUBJECT_SKILL_DIVERSITY, new Judgment(2.0, 0.30));
        assertThat(policy.decide(0.88, judgments).status()).isEqualTo(Status.READY_WITH_CAUTION);
    }

    // ---- the seven calibration combinations ----

    @Test
    void theSevenOfflineCalibrationCasesAreLabelledAndComplete() throws Exception {
        var judged = ALevelV2Calibration.load(ALevelV2Calibration.OFFLINE_JUDGMENTS);

        assertThat(judged.source()).isEqualTo("OFFLINE_CALIBRATION");
        assertThat(judged.schemaVersion()).isEqualTo("a-level-v2");
        assertThat(judged.cases().keySet()).containsExactlyElementsOf(ALevelV2CalibrationCases.ALL.stream()
                .map(ALevelV2CalibrationCases.CalibrationCase::id).toList());
        judged.cases().values().forEach(value -> assertThat(value.judgments().keySet())
                .containsExactlyInAnyOrderElementsOf(ALevelV2CandidateSchema.SCORE_DIMENSIONS));

        var rows = new ALevelV2Calibration().run(judged);
        assertThat(rows).hasSize(7).allSatisfy(row -> assertThat(row.decision().status())
                .isNotEqualTo(Status.MORE_INFORMATION_REQUIRED));
        assertThat(row(rows, "sociology-psychology-biology").decision().status()).isEqualTo(Status.READY_WITH_CAUTION);
        assertThat(row(rows, "art-english-literature-psychology").decision().measures()
                .get(Measure.ANALYTICAL_AND_QUANTITATIVE).lowerConfidence()).isTrue();
        // The four combinations with a v1 development fixture also carry the v1 baseline.
        assertThat(rows.stream().filter(row -> !row.v1Indicators().isEmpty())).hasSize(4);
    }

    @Test
    void noCombinationIsTopOnEveryMeasure() throws Exception {
        var rows = new ALevelV2Calibration().run(ALevelV2Calibration.load(ALevelV2Calibration.OFFLINE_JUDGMENTS));

        for (var row : rows) {
            boolean topEverywhere = java.util.Arrays.stream(Measure.values()).allMatch(measure -> rows.stream()
                    .allMatch(other -> indicator(other.decision(), measure) <= indicator(row.decision(), measure)));
            assertThat(topEverywhere).as(row.calibrationCase().id()).isFalse();
        }
    }

    /** Opt-in only: {@code -Da-level.calibration.enabled=true}. Writes the calibration report under target/. */
    @Test
    @EnabledIfSystemProperty(named = "a-level.calibration.enabled", matches = "true")
    void writesTheOfflineCalibrationReport() throws Exception {
        var calibration = new ALevelV2Calibration();
        var judged = ALevelV2Calibration.load(ALevelV2Calibration.OFFLINE_JUDGMENTS);
        Path report = Path.of("target", "a-level-calibration", "a-level-v2-offline-calibration.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, calibration.report(judged, calibration.run(judged)));
        assertThat(Files.readString(report)).contains("Judgment source: OFFLINE_CALIBRATION");
    }

    // ---- helpers ----

    /** Every analytical-side dimension at {@code analytical}, every communication-side one at {@code communication}. */
    private static Map<String, Judgment> uniform(double analytical, double communication) {
        Map<String, Judgment> judgments = new LinkedHashMap<>();
        for (String dimension : List.of(QUANTITATIVE_REASONING, ANALYTICAL_REASONING, TECHNICAL_DIGITAL_FOUNDATION)) {
            judgments.put(dimension, new Judgment(analytical, 0.85));
        }
        for (String dimension : List.of(WRITTEN_VERBAL_COMMUNICATION, ORIGINAL_JUDGMENT_CREATIVITY,
                CONTEXTUAL_SOCIAL_UNDERSTANDING)) {
            judgments.put(dimension, new Judgment(communication, 0.85));
        }
        judgments.put(SUBJECT_SKILL_DIVERSITY, new Judgment(2.4, 0.85));
        judgments.put(DISCIPLINE_FAMILY_COVERAGE, new Judgment(2.6, 0.85));
        judgments.put(AI_LEARNING_SUPPORT, new Judgment(3.0, 0.85));
        return judgments;
    }

    private static double indicator(ALevelV2CandidatePolicy.Decision decision, Measure measure) {
        return decision.measures().get(measure).indicator();
    }

    private static ALevelV2Calibration.Row row(List<ALevelV2Calibration.Row> rows, String id) {
        return rows.stream().filter(row -> row.calibrationCase().id().equals(id)).findFirst().orElseThrow();
    }
}
