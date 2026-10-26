package com.chikere.futureai.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Objects;

/**
 * Renders a decision policy's own intermediate values as assessment-trace payloads.
 *
 * <p>Nothing here calculates a score. Every number comes from the records and values the policy used,
 * so the policy remains the only scoring implementation.</p>
 */
public final class DecisionTracePayloads {

    private DecisionTracePayloads() {
    }

    /** One measure's full calculation: inputs, transformations, weights, contributions, score and band. */
    public static Map<String, Object> measure(String policyVersion, Object measure, String calculation,
            List<WeightedContribution> inputs, double weightedTotal, double score, Object band,
            Map<String, String> bandThresholds, double measureConfidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", policyVersion);
        payload.put("measure", measure);
        payload.put("calculation", calculation);
        payload.put("scoreScale", "judgment scores are 0-4; transformed values are 0-1");
        payload.put("inputs", inputs);
        payload.put("weightedTotal", weightedTotal);
        payload.put("scoreFormula", "weightedTotal x 10, clamped to 0-10");
        payload.put("unroundedScore", score);
        payload.put("band", band);
        payload.put("bandThresholds", bandThresholds);
        payload.put("measureConfidence", measureConfidence);
        payload.put("measureConfidenceFormula", "sum of weight x judgment confidence");
        payload.put("note", "Scores are shown to visitors rounded by the view layer; this is the stored value.");
        return payload;
    }

    /** The information-sufficiency gate, and whether scores were produced or suppressed because of it. */
    public static Map<String, Object> informationSufficiency(String policyVersion, double value,
            double requiredThreshold, double readyThreshold, Object status, Object confidence,
            boolean scoresProduced) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", policyVersion);
        payload.put("informationSufficiency", value);
        payload.put("moreInformationRequiredBelow", requiredThreshold);
        payload.put("readyRequiresAtLeast", readyThreshold);
        payload.put("readyAlsoRequiresConfidence", "HIGH");
        payload.put("status", status);
        payload.put("assessmentConfidence", confidence);
        payload.put("scoresProduced", scoresProduced);
        payload.put("reason", scoresProduced
                ? (Objects.toString(status).equals("READY")
                        ? "information sufficiency and confidence both meet the READY thresholds"
                        : "scores produced with caution: information sufficiency below " + readyThreshold
                                + " or assessment confidence below HIGH")
                : "information sufficiency below " + requiredThreshold + ": measures, drivers and factors suppressed");
        return payload;
    }

    /** How the overall assessment confidence was chosen from the judgments' own confidence values. */
    public static Map<String, Object> confidence(String policyVersion, double informationSufficiency,
            double meanConfidence, double minimumConfidence, Map<String, Double> thresholds, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", policyVersion);
        payload.put("informationSufficiency", informationSufficiency);
        payload.put("meanJudgmentConfidence", meanConfidence);
        payload.put("minimumJudgmentConfidence", minimumConfidence);
        payload.put("thresholds", thresholds);
        payload.put("assessmentConfidence", result);
        return payload;
    }

    /**
     * Every candidate factor, how it ranked within its list, and why it was or was not displayed.
     *
     * @param positive the policy's ranked list of positive-effect factors, before the display limit
     * @param negative the policy's ranked list of negative-effect factors, before the display limit
     */
    public static Map<String, Object> factorSelection(String policyVersion, String basis,
            List<FactorCandidate> candidates, List<String> positive, List<String> negative, int limit,
            String positiveList, String negativeList) {
        return factorSelection(policyVersion, basis, candidates, positive, negative, limit, positiveList,
                negativeList, OptionalDouble.empty());
    }

    /**
     * As {@link #factorSelection(String, String, List, List, List, int, String, String)}, also recording
     * the materiality test when the policy applies one. Every candidate stays in the payload, material or
     * not, so future calibration can see what was left out and by how much.
     *
     * @param materialityThreshold the minimum absolute signed effect for selection, or empty when none applies
     */
    public static Map<String, Object> factorSelection(String policyVersion, String basis,
            List<FactorCandidate> candidates, List<String> positive, List<String> negative, int limit,
            String positiveList, String negativeList, OptionalDouble materialityThreshold) {
        List<Map<String, Object>> rendered = new ArrayList<>();
        for (FactorCandidate candidate : candidates) {
            Map<String, Object> factor = new LinkedHashMap<>();
            factor.put("dimensionId", candidate.dimensionId());
            factor.put("rawScore", candidate.rawScore());
            factor.put("transformedValue", candidate.transformedValue());
            factor.put("weight", candidate.weight());
            factor.put("signedEffect", candidate.signedEffect());
            double effect = candidate.signedEffect();
            factor.put("sign", effect > 0.0 ? "POSITIVE" : effect < 0.0 ? "NEGATIVE" : "ZERO");
            List<String> ranking = effect > 0.0 ? positive : effect < 0.0 ? negative : List.of();
            String list = effect > 0.0 ? positiveList : effect < 0.0 ? negativeList : null;
            int rank = ranking.indexOf(candidate.dimensionId()) + 1;
            // The policy's ranked lists hold only the factors that passed its materiality test.
            boolean material = effect != 0.0 && (materialityThreshold.isEmpty() || rank > 0);
            boolean selected = rank > 0 && rank <= limit;
            factor.put("eligible", effect != 0.0 && material);
            if (materialityThreshold.isPresent()) {
                factor.put("absoluteEffect", Math.abs(effect));
                factor.put("materialityThreshold", materialityThreshold.getAsDouble());
                factor.put("material", material);
            }
            factor.put("list", material ? list : null);
            factor.put("rank", rank > 0 ? rank : null);
            factor.put("selected", selected);
            factor.put("reason", effect == 0.0
                    ? "score at the neutral midpoint, so no effect: never displayed"
                    : !material
                    ? "excluded: absolute signed effect " + Math.abs(effect) + " < materiality threshold "
                            + materialityThreshold.getAsDouble()
                    : selected
                            ? "ranked " + rank + " of " + ranking.size() + " in " + list + ", within the top " + limit
                            : "ranked " + rank + " of " + ranking.size() + " in " + list + ", outside the top " + limit);
            rendered.add(factor);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", policyVersion);
        payload.put("basis", basis);
        payload.put("formula", "signedEffect = weight x (transformedValue - 0.5)");
        payload.put("ordering", "positive list by signedEffect descending; negative list by signedEffect ascending");
        payload.put("limitPerList", limit);
        materialityThreshold.ifPresent(threshold -> payload.put("materiality",
                "a factor is selected only when abs(signedEffect) >= " + threshold
                        + "; smaller effects still count towards the score"));
        payload.put("candidates", rendered);
        payload.put(positiveList, positive.subList(0, Math.min(limit, positive.size())));
        payload.put(negativeList, negative.subList(0, Math.min(limit, negative.size())));
        return payload;
    }
}
