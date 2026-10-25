package com.chikere.futureai.decision;

/**
 * One judgment's part in a weighted measure, as the decision policy actually computed it.
 *
 * <p>The policies sum these records' {@code contribution} and {@code weightedConfidence} to produce
 * a measure, and the assessment trace prints the same records. There is one calculation, so the trace
 * cannot disagree with the score.</p>
 *
 * @param rawScore the judgment's 0-4 score as supplied by the decision model
 * @param transformation how the raw score became a 0-1 value, for example {@code normalise} or {@code inverse}
 */
public record WeightedContribution(
        String dimensionId,
        double rawScore,
        String transformation,
        double transformedValue,
        double weight,
        double contribution,
        double judgmentConfidence,
        double weightedConfidence) {

    public static WeightedContribution of(String dimensionId, double rawScore, String transformation,
            double transformedValue, double weight, double judgmentConfidence) {
        return new WeightedContribution(dimensionId, rawScore, transformation, transformedValue, weight,
                weight * transformedValue, judgmentConfidence, weight * judgmentConfidence);
    }
}
