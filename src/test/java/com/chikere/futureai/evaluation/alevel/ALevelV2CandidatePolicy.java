package com.chikere.futureai.evaluation.alevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.AI_ERA_COMPLEMENTARITY;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.ANALYTICAL_REASONING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.CONTEXTUAL_SOCIAL_UNDERSTANDING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.DISCIPLINE_FAMILY_COVERAGE;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.ORIGINAL_JUDGMENT_CREATIVITY;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.QUANTITATIVE_REASONING;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.SCORE_DIMENSIONS;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.SUBJECT_SKILL_DIVERSITY;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.TECHNICAL_DIGITAL_FOUNDATION;
import static com.chikere.futureai.evaluation.alevel.ALevelV2CandidateSchema.WRITTEN_VERBAL_COMMUNICATION;

/**
 * Stage A1/A2 design: the proposed {@code a-level-decision-v2}, test-side until approved. Pure deterministic Java over
 * {@code a-level-v2} judgments; it names no subject and has no subject-specific branch.
 *
 * <p>There is no headline score. Three measures are shown, the second as two separate sides that are never added
 * together:</p>
 * <ol>
 *   <li>Options Kept Open (Focused / Balanced / Broad)</li>
 *   <li>Capability Profile: Analytical &amp; Quantitative, and Communication, Interpretation &amp; Context
 *       (Lighter / Moderate / Strong emphasis)</li>
 *   <li>Learning Alongside AI (Low / Moderate / High)</li>
 * </ol>
 *
 * <p>Thresholds, materiality, factor counts and confidence rules are the approved {@code university-decision-v2}
 * ones, so the two student journeys behave the same way.</p>
 */
final class ALevelV2CandidatePolicy {

    static final String POLICY_VERSION = "a-level-decision-v2";

    static final double INFORMATION_REQUIRED_THRESHOLD = 0.60;
    static final double READY_INFORMATION_THRESHOLD = 0.75;
    static final double MIDDLE_FROM = 3.50;
    static final double TOP_FROM = 7.00;
    /** A factor is material when it moves its measure by at least this much on the 0–10 scale. */
    static final double MATERIALITY = 0.25;
    static final double LOWER_CONFIDENCE_BELOW = 0.60;
    static final int LOWER_CONFIDENCE_MEASURES_FOR_CAUTION = 2;
    static final int MAXIMUM_FACTORS_PER_DIRECTION = 2;
    static final double TOLERANCE = 1.0e-9;

    /**
     * One scored dimension of the free result.
     *
     * @param showsLimits whether limiting factors are ever explained. The two capability sides never explain limits:
     *     a lighter emphasis on one side is the shape of the combination, not a shortfall.
     */
    enum Measure {
        OPTIONS_KEPT_OPEN("Options Kept Open", List.of("Focused", "Balanced", "Broad"), true,
                weights(DISCIPLINE_FAMILY_COVERAGE, 0.60, SUBJECT_SKILL_DIVERSITY, 0.40)),
        ANALYTICAL_AND_QUANTITATIVE("Analytical & Quantitative",
                List.of("Lighter emphasis", "Moderate emphasis", "Strong emphasis"), false,
                weights(QUANTITATIVE_REASONING, 0.40, ANALYTICAL_REASONING, 0.40, TECHNICAL_DIGITAL_FOUNDATION, 0.20)),
        COMMUNICATION_INTERPRETATION_AND_CONTEXT("Communication, Interpretation & Context",
                List.of("Lighter emphasis", "Moderate emphasis", "Strong emphasis"), false,
                weights(WRITTEN_VERBAL_COMMUNICATION, 0.40, ORIGINAL_JUDGMENT_CREATIVITY, 0.30,
                        CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.30)),
        LEARNING_ALONGSIDE_AI("Learning Alongside AI", List.of("Low", "Moderate", "High"), true,
                weights(AI_ERA_COMPLEMENTARITY, 0.70, ANALYTICAL_REASONING, 0.15, ORIGINAL_JUDGMENT_CREATIVITY, 0.15));

        final String displayName;
        final List<String> bandLabels;
        final boolean showsLimits;
        final Map<String, Double> weights;

        Measure(String displayName, List<String> bandLabels, boolean showsLimits, Map<String, Double> weights) {
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(total - 1.0) > TOLERANCE) {
                throw new IllegalStateException(name() + " weights must sum to 1 but summed to " + total);
            }
            if (!SCORE_DIMENSIONS.containsAll(weights.keySet())) {
                throw new IllegalStateException(name() + " uses an unknown dimension");
            }
            this.displayName = displayName;
            this.bandLabels = bandLabels;
            this.showsLimits = showsLimits;
            this.weights = weights;
        }
    }

    enum Status { MORE_INFORMATION_REQUIRED, READY_WITH_CAUTION, READY }

    record Judgment(double score, double confidence) {
        Judgment {
            if (!Double.isFinite(score) || score < 0.0 || score > 4.0) {
                throw new IllegalArgumentException("score must be between 0 and 4");
            }
            if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
        }
    }

    record Factor(String dimension, double score, double weight, double effect) {
    }

    /**
     * @param band 0, 1 or 2: the lower, middle or top band
     * @param shownScore the boundary-safe two-decimal score, never contradicting {@code band}
     */
    record MeasureResult(Measure measure, double indicator, int band, String shownScore, String bandLabel,
            double confidence, boolean lowerConfidence, List<Factor> contributions, List<Factor> drivers,
            List<Factor> limits) {
    }

    record Decision(Status status, double informationSufficiency, Map<Measure, MeasureResult> measures) {
        /** There is never an overall indicator. */
        Optional<Double> overallIndicator() {
            return Optional.empty();
        }
    }

    Decision decide(double informationSufficiency, Map<String, Judgment> judgments) {
        if (!Double.isFinite(informationSufficiency) || informationSufficiency < 0.0 || informationSufficiency > 1.0) {
            throw new IllegalArgumentException("informationSufficiency must be between 0 and 1");
        }
        if (!judgments.keySet().equals(java.util.Set.copyOf(SCORE_DIMENSIONS))) {
            throw new IllegalArgumentException("exactly the a-level-v2 score dimensions are required");
        }
        if (below(informationSufficiency, INFORMATION_REQUIRED_THRESHOLD)) {
            return new Decision(Status.MORE_INFORMATION_REQUIRED, informationSufficiency, Map.of());
        }
        Map<Measure, MeasureResult> measures = new LinkedHashMap<>();
        for (Measure measure : Measure.values()) {
            measures.put(measure, measure(measure, judgments));
        }
        long lowerConfidence = measures.values().stream().filter(MeasureResult::lowerConfidence).count();
        Status status = below(informationSufficiency, READY_INFORMATION_THRESHOLD)
                || lowerConfidence >= LOWER_CONFIDENCE_MEASURES_FOR_CAUTION ? Status.READY_WITH_CAUTION : Status.READY;
        return new Decision(status, informationSufficiency, Map.copyOf(measures));
    }

    private MeasureResult measure(Measure measure, Map<String, Judgment> judgments) {
        double composite = 0.0;
        double confidence = 0.0;
        List<Factor> contributions = new java.util.ArrayList<>();
        for (var weight : measure.weights.entrySet()) {
            Judgment judgment = judgments.get(weight.getKey());
            double normalised = judgment.score() / 4.0;
            composite += weight.getValue() * normalised;
            confidence += weight.getValue() * judgment.confidence();
            contributions.add(new Factor(weight.getKey(), judgment.score(), weight.getValue(),
                    10.0 * weight.getValue() * (normalised - 0.5)));
        }
        double indicator = Math.max(0.0, Math.min(10.0, 10.0 * composite));
        int band = band(indicator);
        Comparator<Factor> largestFirst = Comparator
                .<Factor>comparingLong(factor -> -Math.round(Math.abs(factor.effect()) / TOLERANCE))
                .thenComparingInt(factor -> SCORE_DIMENSIONS.indexOf(factor.dimension()));
        List<Factor> ranked = contributions.stream().sorted(largestFirst).toList();
        List<Factor> drivers = ranked.stream().filter(factor -> factor.effect() > 0.0 && !below(factor.effect(),
                MATERIALITY)).limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        // Limits are explained only where the measure has them at all, and never in its top band.
        List<Factor> limits = !measure.showsLimits || band == 2 ? List.of() : ranked.stream()
                .filter(factor -> factor.effect() < 0.0 && !below(-factor.effect(), MATERIALITY))
                .limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        confidence = Math.min(1.0, confidence);
        return new MeasureResult(measure, indicator, band, shownScore(indicator, band), measure.bandLabels.get(band),
                confidence, below(confidence, LOWER_CONFIDENCE_BELOW), ranked, drivers, limits);
    }

    static int band(double indicator) {
        if (below(indicator, MIDDLE_FROM)) return 0;
        if (below(indicator, TOP_FROM)) return 1;
        return 2;
    }

    /** The University v2 rule: two decimals, half-up, never visually reaching the next band's boundary. */
    static String shownScore(double indicator, int band) {
        BigDecimal rounded = BigDecimal.valueOf(indicator).setScale(2, RoundingMode.HALF_UP);
        Optional<BigDecimal> upper = switch (band) {
            case 0 -> Optional.of(BigDecimal.valueOf(MIDDLE_FROM));
            case 1 -> Optional.of(BigDecimal.valueOf(TOP_FROM));
            default -> Optional.empty();
        };
        return upper.filter(boundary -> rounded.compareTo(boundary) >= 0)
                .map(boundary -> boundary.setScale(2, RoundingMode.HALF_UP).subtract(new BigDecimal("0.01")))
                .orElse(rounded).toPlainString();
    }

    static boolean below(double value, double threshold) {
        return value < threshold - TOLERANCE;
    }

    private static Map<String, Double> weights(Object... dimensionsAndWeights) {
        LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        for (int index = 0; index < dimensionsAndWeights.length; index += 2) {
            if (weights.put((String) dimensionsAndWeights[index], (Double) dimensionsAndWeights[index + 1]) != null) {
                throw new IllegalStateException("duplicate dimension " + dimensionsAndWeights[index]);
            }
        }
        return java.util.Collections.unmodifiableMap(weights);
    }

    /** Neutral, factual phrases for drivers and limits; no comparison with other students or combinations. */
    static String phrase(Measure measure, Factor factor, boolean driver) {
        Objects.requireNonNull(measure);
        return switch (factor.dimension()) {
            case DISCIPLINE_FAMILY_COVERAGE -> driver ? "Your subjects span distinct academic families"
                    : "Your subjects sit within closely related academic families";
            case SUBJECT_SKILL_DIVERSITY -> driver ? "Your subjects build different kinds of capability"
                    : "Your subjects build similar kinds of capability";
            case AI_ERA_COMPLEMENTARITY -> driver ? "Much of the learning in these subjects lends itself to AI support"
                    : "Less of the learning in these subjects lends itself to AI support";
            default -> {
                String capability = capability(factor.dimension());
                yield driver ? capability : "Less emphasis on " + Character.toLowerCase(capability.charAt(0))
                        + capability.substring(1);
            }
        };
    }

    private static String capability(String dimension) {
        return switch (dimension) {
            case QUANTITATIVE_REASONING -> "Mathematical and statistical reasoning";
            case ANALYTICAL_REASONING -> "Structured analysis and problem solving";
            case TECHNICAL_DIGITAL_FOUNDATION -> "Technical and computational work";
            case WRITTEN_VERBAL_COMMUNICATION -> "Extended writing and argument";
            case ORIGINAL_JUDGMENT_CREATIVITY -> "Interpretation, originality and design";
            case CONTEXTUAL_SOCIAL_UNDERSTANDING -> "Understanding human, social and historical context";
            default -> throw new IllegalArgumentException("no phrase for " + dimension);
        };
    }
}
