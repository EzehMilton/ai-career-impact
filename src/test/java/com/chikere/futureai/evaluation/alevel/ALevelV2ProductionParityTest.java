package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileFactor;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevStateMapper;
import com.chikere.futureai.report.alevel.application.ALevelSubjectProfileComposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Production {@code a-level-v2} and {@code a-level-decision-v2} must be exactly the approved Stage A1/A2 design from
 * {@code ba426a1}: the same questions, weights, bands, factors and confidence on every calibration combination.
 */
class ALevelV2ProductionParityTest {

    @Test
    void theProductionSchemaAsksExactlyTheApprovedQuestions() {
        var production = new ALevelJudgmentSchemaV2();
        var approved = new ALevelV2CandidateSchema();

        assertThat(production.version()).isEqualTo(approved.version());
        assertThat(production.questions().keySet()).containsExactlyElementsOf(approved.questions().keySet());
        assertThat(production.questions()).isEqualTo(approved.questions());
        assertThat(ALevelDecisionPolicyV2.SCORE_DIMENSIONS).containsExactlyElementsOf(ALevelV2CandidateSchema.SCORE_DIMENSIONS);
    }

    @Test
    void theProductionWeightsAreTheApprovedOnes() {
        for (var approved : ALevelV2CandidatePolicy.Measure.values()) {
            assertThat(ALevelDecisionPolicyV2.weightsFor(ALevelProfileMeasure.valueOf(approved.name())))
                    .as(approved.name()).isEqualTo(approved.weights);
            assertThat(ALevelProfileMeasure.valueOf(approved.name()).displayName()).isEqualTo(approved.displayName);
        }
        assertThat(ALevelProfileMeasure.values()).hasSameSizeAs(ALevelV2CandidatePolicy.Measure.values());
        assertThat(ALevelDecisionPolicyV2.MATERIALITY).isEqualTo(ALevelV2CandidatePolicy.MATERIALITY);
        assertThat(ALevelDecisionPolicyV2.LOWER_CONFIDENCE_BELOW).isEqualTo(ALevelV2CandidatePolicy.LOWER_CONFIDENCE_BELOW);
        assertThat(ALevelDecisionPolicyV2.MODERATE_FROM).isEqualTo(ALevelV2CandidatePolicy.MIDDLE_FROM);
        assertThat(ALevelDecisionPolicyV2.HIGH_FROM).isEqualTo(ALevelV2CandidatePolicy.TOP_FROM);
    }

    @Test
    void theProductionPolicyReproducesEveryApprovedCalibrationResult() throws Exception {
        var judged = ALevelV2Calibration.load(ALevelV2Calibration.OFFLINE_JUDGMENTS);
        var approvedRows = new ALevelV2Calibration().run(judged);
        var production = new ALevelDecisionPolicyV2();

        for (var row : approvedRows) {
            var result = production.decide(judgmentSet(row.judgments()));
            assertThat(result.assessmentDecision().status().name()).as(row.calibrationCase().id())
                    .isEqualTo(row.decision().status().name());
            for (var approved : row.decision().measures().values()) {
                var measure = result.measure(ALevelProfileMeasure.valueOf(approved.measure().name())).orElseThrow();
                String label = row.calibrationCase().id() + " " + approved.measure();
                assertThat(measure.indicator()).as(label).isCloseTo(approved.indicator(), within(1e-12));
                assertThat(measure.band().ordinal()).as(label).isEqualTo(approved.band());
                assertThat(measure.confidence()).as(label).isCloseTo(approved.confidence(), within(1e-12));
                assertThat(measure.lowerConfidence()).as(label).isEqualTo(approved.lowerConfidence());
                assertThat(measure.drivers()).extracting(ALevelProfileFactor::dimensionId).as(label)
                        .containsExactlyElementsOf(approved.drivers().stream()
                                .map(ALevelV2CandidatePolicy.Factor::dimension).toList());
                assertThat(measure.limitingFactors()).extracting(ALevelProfileFactor::dimensionId).as(label)
                        .containsExactlyElementsOf(approved.limits().stream()
                                .map(ALevelV2CandidatePolicy.Factor::dimension).toList());
                // The production score display and band wording are the approved ones.
                assertThat(ALevelSubjectProfileComposer.score(measure.indicator(), measure.band())).as(label)
                        .isEqualTo(approved.shownScore());
                assertThat(ALevelSubjectProfileComposer.bandLabel(measure.measure(), measure.band())).as(label)
                        .isEqualTo(approved.bandLabel());
            }
        }
        assertThat(approvedRows).hasSize(7);
        assertThat(approvedRows).anySatisfy(row -> assertThat(production.decide(judgmentSet(row.judgments()))
                .assessmentDecision().status()).isEqualTo(AssessmentStatus.READY_WITH_CAUTION));
    }

    @Test
    void theProductionJudgmentInputIsTheApprovedSubjectsOnlyInput() {
        for (var calibrationCase : ALevelV2CalibrationCases.ALL) {
            assertThat(new ALevelV2JevStateMapper().map(ALevelJudgmentInput.of(calibrationCase.state())))
                    .as(calibrationCase.id()).isEqualTo(ALevelV2JudgmentInput.map(calibrationCase.state()));
        }
    }

    private static AssessmentJudgmentSet judgmentSet(ALevelV2Calibration.CaseJudgments judgments) {
        List<DimensionJudgment> values = new ArrayList<>();
        values.add(new DimensionJudgment("information_sufficient", "Information sufficiency",
                OptionalDouble.of(judgments.information()), Optional.empty(), Optional.empty(), OptionalDouble.empty(),
                Map.of()));
        judgments.judgments().forEach((id, judgment) -> values.add(new DimensionJudgment(id, id,
                OptionalDouble.of(judgment.score()), Optional.empty(), Optional.empty(),
                OptionalDouble.of(judgment.confidence()), Map.of())));
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, "a-level-v2", values);
    }
}
