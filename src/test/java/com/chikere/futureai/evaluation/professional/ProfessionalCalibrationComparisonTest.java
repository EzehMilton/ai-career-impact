package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.decision.professional.ProfessionalMeasureResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalCalibrationComparisonTest {

    @Test
    void flagsClassificationAndLargeIndicatorChangesButNeverRequiresProviderValueEquality() {
        var evaluationCase = ProfessionalEvaluationDataset.load().stream()
                .filter(value -> value.id().equals("classification_boundary_digital_adviser"))
                .findFirst().orElseThrow();
        var jev = result(evaluationCase, decision(5.9, IndicatorBand.MODERATE, AssessmentStatus.READY));
        var openAi = result(evaluationCase, decision(6.5, IndicatorBand.HIGH, AssessmentStatus.READY));

        var comparison = new ProfessionalCalibrationComparison()
                .compare(List.of(jev), List.of(openAi)).getFirst();

        assertThat(comparison.materialDifferences())
                .extracting(ProfessionalCalibrationComparison.Difference::field)
                .contains("assessment.label", "measure.AI_EXPOSURE.band", "measure.AI_EXPOSURE.indicator");
        assertThat(ProfessionalCalibrationReportWriter.render(List.of(comparison)))
                .contains("Cases with material customer-facing differences: 1 / 1")
                .contains("Crosses a customer-facing classification boundary");
    }

    @Test
    void datasetIncludesMissingAmbiguousContradictoryAndBoundaryCases() {
        assertThat(ProfessionalEvaluationDataset.load())
                .extracting(ProfessionalEvaluationCase::id)
                .containsAll(ProfessionalEvaluationDataset.REQUIRED_CALIBRATION_CASES);
    }

    private static ProfessionalEvaluationResult result(ProfessionalEvaluationCase evaluationCase,
            ProfessionalDecisionResult decision) {
        return new ProfessionalEvaluationResult(evaluationCase,
                new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, "professional-v1", List.of()), decision);
    }

    private static ProfessionalDecisionResult decision(double exposure, IndicatorBand exposureBand,
            AssessmentStatus status) {
        EnumMap<ProfessionalMeasure, ProfessionalMeasureResult> measures = new EnumMap<>(ProfessionalMeasure.class);
        for (ProfessionalMeasure measure : ProfessionalMeasure.values()) {
            double indicator = measure == ProfessionalMeasure.AI_EXPOSURE ? exposure : 5.0;
            IndicatorBand band = measure == ProfessionalMeasure.AI_EXPOSURE ? exposureBand : IndicatorBand.MODERATE;
            measures.put(measure, new ProfessionalMeasureResult(measure, indicator, band, 0.8));
        }
        var headline = new AssessmentDecision(JourneyType.PROFESSIONAL, OptionalDouble.of(exposure),
                exposureBand + " EXPOSURE", AssessmentConfidence.HIGH, status, List.of(), List.of());
        return new ProfessionalDecisionResult("professional-decision-v2", 0.9, headline, measures,
                List.of(), List.of());
    }
}
