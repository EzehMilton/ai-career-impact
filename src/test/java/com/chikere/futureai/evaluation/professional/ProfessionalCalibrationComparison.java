package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares customer-facing deterministic outcomes, never provider probability equality. */
final class ProfessionalCalibrationComparison {

    static final double MATERIAL_INDICATOR_DELTA = 0.50;

    record Difference(String field, String jevValue, String openAiValue, boolean material, String reason) {}

    record CaseComparison(String caseId, ProfessionalEvaluationResult jev, ProfessionalEvaluationResult openAi,
            List<Difference> differences) {
        List<Difference> materialDifferences() {
            return differences.stream().filter(Difference::material).toList();
        }
    }

    List<CaseComparison> compare(List<ProfessionalEvaluationResult> jev,
            List<ProfessionalEvaluationResult> openAi) {
        Map<String, ProfessionalEvaluationResult> jevById = byId(jev, "Jev");
        Map<String, ProfessionalEvaluationResult> openAiById = byId(openAi, "OpenAI");
        if (!jevById.keySet().equals(openAiById.keySet())) {
            throw new IllegalArgumentException("Jev and OpenAI calibration case IDs must match");
        }
        return jevById.keySet().stream()
                .map(id -> compare(id, jevById.get(id), openAiById.get(id)))
                .toList();
    }

    private CaseComparison compare(String id, ProfessionalEvaluationResult jev, ProfessionalEvaluationResult openAi) {
        ProfessionalDecisionResult left = jev.decision();
        ProfessionalDecisionResult right = openAi.decision();
        List<Difference> differences = new ArrayList<>();
        add(differences, "assessment.status", left.assessmentDecision().status(), right.assessmentDecision().status(),
                true, "Changes whether the assessment is ready or requires caution/more information");
        add(differences, "assessment.confidence", left.assessmentDecision().confidence(),
                right.assessmentDecision().confidence(), true, "Changes customer-facing assessment confidence");
        add(differences, "assessment.label", left.assessmentDecision().resultLabel(),
                right.assessmentDecision().resultLabel(), true, "Changes the headline result");

        for (ProfessionalMeasure measure : ProfessionalMeasure.values()) {
            var leftMeasure = left.measure(measure);
            var rightMeasure = right.measure(measure);
            if (leftMeasure.isEmpty() || rightMeasure.isEmpty()) {
                add(differences, "measure." + measure + ".available", leftMeasure.isPresent(),
                        rightMeasure.isPresent(), true, "Changes whether an authoritative measure is shown");
                continue;
            }
            add(differences, "measure." + measure + ".band", leftMeasure.get().band(),
                    rightMeasure.get().band(), true, "Crosses a customer-facing classification boundary");
            double delta = rightMeasure.get().indicator() - leftMeasure.get().indicator();
            if (Math.abs(delta) >= MATERIAL_INDICATOR_DELTA) {
                differences.add(new Difference("measure." + measure + ".indicator",
                        format(leftMeasure.get().indicator()), format(rightMeasure.get().indicator()), true,
                        "Indicator moved by " + format(Math.abs(delta)) + " on the 0-10 scale"));
            }
        }

        add(differences, "drivers", factorIds(left.topExposureDrivers()), factorIds(right.topExposureDrivers()),
                true, "Changes the factors explained as increasing AI exposure");
        add(differences, "protectiveFactors", factorIds(left.topProtectiveFactors()),
                factorIds(right.topProtectiveFactors()), true,
                "Changes the factors explained as reducing AI exposure");
        return new CaseComparison(id, jev, openAi, List.copyOf(differences));
    }

    private static void add(List<Difference> differences, String field, Object left, Object right,
            boolean material, String reason) {
        if (!String.valueOf(left).equals(String.valueOf(right))) {
            differences.add(new Difference(field, String.valueOf(left), String.valueOf(right), material, reason));
        }
    }

    private static List<String> factorIds(List<com.chikere.futureai.decision.professional.ProfessionalDecisionFactor> factors) {
        return factors.stream().map(factor -> factor.dimensionId()).toList();
    }

    private static Map<String, ProfessionalEvaluationResult> byId(List<ProfessionalEvaluationResult> results,
            String provider) {
        LinkedHashMap<String, ProfessionalEvaluationResult> byId = new LinkedHashMap<>();
        for (ProfessionalEvaluationResult result : results) {
            String id = result.evaluationCase().id();
            if (byId.put(id, result) != null) throw new IllegalArgumentException("Duplicate " + provider + " case: " + id);
        }
        return byId;
    }

    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
