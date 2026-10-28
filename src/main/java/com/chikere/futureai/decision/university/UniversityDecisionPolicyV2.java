package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.DecisionTracePayloads;
import com.chikere.futureai.decision.WeightedContribution;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Deterministic {@code university-decision-v2} policy over the unchanged {@code university-v1} judgments.
 *
 * <p>A course profile of three measures with no composite headline score, designed in Stage 5B.2 from the
 * frozen live baseline. Every measure is a weighted mean of normalised judgments on a 0-10 scale; its factors are
 * the signed effects of its own formula, and its confidence is the weighted confidence of its own dimensions.
 * The weights and thresholds are provisional product policy, not validated educational research. Changing any of
 * them needs a new policy version.</p>
 *
 * <p>Contextual complexity contributes to all three measures on purpose, in a different role in each: judgement
 * when checking and interpreting AI output, judgement in complex situations, and capability that carries across
 * varying contexts.</p>
 *
 * <p>{@link UniversityDecisionPolicyV1} is untouched and keeps deciding the meaning of historical assessments.
 * This class is pure Java: it calls no judgment provider, model, persistence or evidence source.</p>
 */
public final class UniversityDecisionPolicyV2 {

    public static final String POLICY_VERSION = "university-decision-v2";
    public static final String SUPPORTED_SCHEMA_VERSION = "university-v1";

    public static final double INFORMATION_REQUIRED_THRESHOLD = 0.60;
    public static final double READY_INFORMATION_THRESHOLD = 0.75;
    public static final double MODERATE_FROM = 3.50;
    public static final double HIGH_FROM = 7.00;
    /** A factor is material when it moves its measure by at least this many points. */
    public static final double MATERIALITY = 0.25;
    /** A measure whose weighted confidence is below this is marked as lower confidence. */
    public static final double LOWER_CONFIDENCE_BELOW = 0.60;
    /** This many lower-confidence measures make the whole profile READY_WITH_CAUTION. */
    public static final int LOWER_CONFIDENCE_MEASURES_FOR_CAUTION = 2;
    public static final int MAXIMUM_FACTORS_PER_DIRECTION = 2;
    /** Floating-point allowance for every threshold comparison, far below any displayed precision. */
    static final double TOLERANCE = 1.0e-9;

    private static final String INFORMATION_SUFFICIENT = "information_sufficient";
    private static final String TECHNICAL_DIGITAL_DEPTH = "technical_digital_depth";
    private static final String ANALYTICAL_REASONING = "analytical_reasoning";
    private static final String ORIGINAL_JUDGMENT_CREATIVITY = "original_judgment_creativity";
    private static final String HUMAN_RELATIONSHIP_DEPENDENCY = "human_relationship_dependency";
    private static final String CONTEXTUAL_COMPLEXITY = "contextual_complexity";
    private static final String PRACTICAL_REAL_WORLD_APPLICATION = "practical_real_world_application";
    private static final String AI_COMPLEMENTARITY = "ai_complementarity";
    private static final String SKILL_TRANSFERABILITY = "skill_transferability";
    private static final String CAREER_BREADTH = "career_breadth";
    private static final List<String> SCHEMA_ORDER = UniversityDecisionPolicyV1.SCORE_DIMENSIONS;
    private static final Map<UniversityProfileMeasure, Map<String, Double>> WEIGHTS = weights();

    private final AssessmentTrace trace;

    public UniversityDecisionPolicyV2() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the values this policy computes; it never changes a decision. */
    public UniversityDecisionPolicyV2(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityProfileDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        Validated judgments = validate(judgmentSet);
        double information = judgments.informationSufficiency();

        if (below(information, INFORMATION_REQUIRED_THRESHOLD)) {
            traceStatus(information, AssessmentStatus.MORE_INFORMATION_REQUIRED, 0);
            return new UniversityProfileDecisionResult(POLICY_VERSION, information,
                    decision("MORE INFORMATION REQUIRED", AssessmentStatus.MORE_INFORMATION_REQUIRED,
                            AssessmentConfidence.LOW), Map.of());
        }

        EnumMap<UniversityProfileMeasure, UniversityProfileMeasureResult> measures =
                new EnumMap<>(UniversityProfileMeasure.class);
        for (UniversityProfileMeasure measure : UniversityProfileMeasure.values()) {
            measures.put(measure, measure(measure, judgments));
        }
        long lowerConfidence = measures.values().stream().filter(UniversityProfileMeasureResult::lowerConfidence)
                .count();
        AssessmentStatus status = below(information, READY_INFORMATION_THRESHOLD)
                || lowerConfidence >= LOWER_CONFIDENCE_MEASURES_FOR_CAUTION
                ? AssessmentStatus.READY_WITH_CAUTION : AssessmentStatus.READY;
        // Internal and compatibility only, never shown to customers; deliberately stricter than the status, so a
        // single lower-confidence measure already lowers it while the status needs two.
        AssessmentConfidence confidence = below(information, READY_INFORMATION_THRESHOLD) || lowerConfidence > 0
                ? AssessmentConfidence.MODERATE : AssessmentConfidence.HIGH;
        traceStatus(information, status, lowerConfidence);
        return new UniversityProfileDecisionResult(POLICY_VERSION, information,
                decision("COURSE PROFILE", status, confidence), measures);
    }

    public static ProfileBand band(double indicator) {
        if (!Double.isFinite(indicator) || indicator < 0.0 || indicator > 10.0) {
            throw new IllegalArgumentException("indicator must be finite and between 0 and 10");
        }
        if (below(indicator, MODERATE_FROM)) {
            return ProfileBand.LOW;
        }
        return below(indicator, HIGH_FROM) ? ProfileBand.MODERATE : ProfileBand.HIGH;
    }

    /** The explicit, testable v2 weights for one measure, in schema order. */
    public static Map<String, Double> weightsFor(UniversityProfileMeasure measure) {
        return WEIGHTS.get(Objects.requireNonNull(measure, "measure must not be null"));
    }

    private UniversityProfileMeasureResult measure(UniversityProfileMeasure measure, Validated judgments) {
        Map<String, Double> weights = WEIGHTS.get(measure);
        List<WeightedContribution> inputs = weights.entrySet().stream().map(weight -> {
            Score score = judgments.score(weight.getKey());
            return WeightedContribution.of(weight.getKey(), score.value(), "normalise: score / 4",
                    UniversityScoreScale.normalise(score.value()), weight.getValue(), score.confidence());
        }).toList();
        double composite = inputs.stream().mapToDouble(WeightedContribution::contribution).sum();
        double confidence = Math.min(1.0, inputs.stream().mapToDouble(WeightedContribution::weightedConfidence).sum());
        double indicator = UniversityScoreScale.toIndicator(composite);
        ProfileBand band = band(indicator);

        Comparator<UniversityProfileFactor> largestFirst = Comparator
                .<UniversityProfileFactor>comparingLong(factor -> -Math.round(Math.abs(factor.effect()) / TOLERANCE))
                .thenComparingInt(factor -> SCHEMA_ORDER.indexOf(factor.dimensionId()));
        List<UniversityProfileFactor> contributions = inputs.stream()
                .map(input -> new UniversityProfileFactor(input.dimensionId(), input.rawScore(), input.weight(),
                        10.0 * input.weight() * (input.transformedValue() - 0.5)))
                .sorted(largestFirst).toList();
        List<UniversityProfileFactor> drivers = contributions.stream()
                .filter(factor -> factor.effect() > 0.0 && !below(factor.effect(), MATERIALITY))
                .limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        List<UniversityProfileFactor> limits = contributions.stream()
                .filter(factor -> factor.effect() < 0.0 && !below(-factor.effect(), MATERIALITY))
                .limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        boolean lowerConfidence = below(confidence, LOWER_CONFIDENCE_BELOW);

        trace.event(JourneyType.UNIVERSITY, TraceStage.SCORE_CALCULATION, () -> DecisionTracePayloads.measure(
                POLICY_VERSION, measure, "WEIGHTED_COMPOSITE", inputs, composite, indicator, band,
                Map.of(ProfileBand.LOW.name(), "score < " + MODERATE_FROM,
                        ProfileBand.MODERATE.name(), MODERATE_FROM + " <= score < " + HIGH_FROM,
                        ProfileBand.HIGH.name(), "score >= " + HIGH_FROM), confidence));
        trace.event(JourneyType.UNIVERSITY, TraceStage.FACTOR_SELECTION, () -> factorTrace(measure, contributions,
                drivers, limits, confidence, lowerConfidence));
        return new UniversityProfileMeasureResult(measure, indicator, band, confidence, lowerConfidence,
                contributions, drivers, limits);
    }

    /**
     * There is no composite headline: the overall indicator stays empty and no headline drivers are named. The
     * customer-facing confidence is per measure; the global value only fills the shared decision record.
     */
    private static AssessmentDecision decision(String label, AssessmentStatus status,
            AssessmentConfidence confidence) {
        return new AssessmentDecision(JourneyType.UNIVERSITY, OptionalDouble.empty(), label, confidence, status,
                List.of(), List.of());
    }

    /** {@code value < threshold}, allowing for floating-point error at the threshold itself. */
    private static boolean below(double value, double threshold) {
        return value < threshold - TOLERANCE;
    }

    private void traceStatus(double information, AssessmentStatus status, long lowerConfidenceMeasures) {
        trace.event(JourneyType.UNIVERSITY, TraceStage.INFORMATION_SUFFICIENCY, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("policyVersion", POLICY_VERSION);
            payload.put("informationSufficiency", information);
            payload.put("moreInformationRequiredBelow", INFORMATION_REQUIRED_THRESHOLD);
            payload.put("readyRequiresAtLeast", READY_INFORMATION_THRESHOLD);
            payload.put("lowerConfidenceMeasures", lowerConfidenceMeasures);
            payload.put("cautionWhenLowerConfidenceMeasuresAtLeast", LOWER_CONFIDENCE_MEASURES_FOR_CAUTION);
            payload.put("status", status);
            payload.put("overallIndicator", "none: a course profile has no composite score");
            return payload;
        });
    }

    private static Map<String, Object> factorTrace(UniversityProfileMeasure measure,
            List<UniversityProfileFactor> contributions, List<UniversityProfileFactor> drivers,
            List<UniversityProfileFactor> limits, double confidence, boolean lowerConfidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", POLICY_VERSION);
        payload.put("measure", measure);
        payload.put("effectFormula", "10 x weight x (score / 4 - 0.5); effects sum to score - 5");
        payload.put("contributions", contributions);
        payload.put("materialityPoints", MATERIALITY);
        payload.put("drivers", drivers.stream().map(UniversityProfileFactor::dimensionId).toList());
        payload.put("limitingFactors", limits.stream().map(UniversityProfileFactor::dimensionId).toList());
        payload.put("measureConfidence", confidence);
        payload.put("lowerConfidence", lowerConfidence);
        return payload;
    }

    private static Validated validate(AssessmentJudgmentSet judgmentSet) {
        Objects.requireNonNull(judgmentSet, "judgmentSet must not be null");
        if (judgmentSet.journey() != JourneyType.UNIVERSITY) {
            throw invalid("journey must be UNIVERSITY");
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(judgmentSet.schemaVersion())) {
            throw invalid("schemaVersion must be " + SUPPORTED_SCHEMA_VERSION);
        }
        Map<String, DimensionJudgment> byId = new HashMap<>();
        for (DimensionJudgment judgment : judgmentSet.judgments()) {
            if (byId.put(judgment.dimensionId(), judgment) != null) {
                throw invalid("duplicate judgment: " + judgment.dimensionId());
            }
        }
        double information = numeric(required(byId, INFORMATION_SUFFICIENT));
        requireRange(information, 0.0, 1.0, INFORMATION_SUFFICIENT + " value");
        Map<String, Score> scores = new HashMap<>();
        for (String dimensionId : SCHEMA_ORDER) {
            DimensionJudgment judgment = required(byId, dimensionId);
            double score = numeric(judgment);
            requireRange(score, 0.0, 4.0, dimensionId + " score");
            if (judgment.confidenceValue().isEmpty()) {
                throw invalid("missing confidence value for " + dimensionId);
            }
            double confidence = judgment.confidenceValue().getAsDouble();
            requireRange(confidence, 0.0, 1.0, dimensionId + " confidence");
            scores.put(dimensionId, new Score(score, confidence));
        }
        return new Validated(information, Map.copyOf(scores));
    }

    private static DimensionJudgment required(Map<String, DimensionJudgment> byId, String dimensionId) {
        DimensionJudgment judgment = byId.get(dimensionId);
        if (judgment == null) {
            throw invalid("missing required judgment: " + dimensionId);
        }
        return judgment;
    }

    private static double numeric(DimensionJudgment judgment) {
        if (judgment.numericValue().isEmpty()) {
            throw invalid("missing numeric value for " + judgment.dimensionId());
        }
        return judgment.numericValue().getAsDouble();
    }

    private static void requireRange(double value, double minimum, double maximum, String description) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw invalid(description + " must be between " + minimum + " and " + maximum);
        }
    }

    private static InvalidUniversityJudgmentSetException invalid(String message) {
        return new InvalidUniversityJudgmentSetException("Invalid university judgment set: " + message);
    }

    private static Map<UniversityProfileMeasure, Map<String, Double>> weights() {
        EnumMap<UniversityProfileMeasure, Map<String, Double>> weights = new EnumMap<>(UniversityProfileMeasure.class);
        weights.put(UniversityProfileMeasure.WORKING_ALONGSIDE_AI, measureWeights(Map.of(
                AI_COMPLEMENTARITY, 0.55,
                TECHNICAL_DIGITAL_DEPTH, 0.15,
                ANALYTICAL_REASONING, 0.15,
                CONTEXTUAL_COMPLEXITY, 0.15)));
        weights.put(UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY, measureWeights(Map.of(
                HUMAN_RELATIONSHIP_DEPENDENCY, 0.30,
                PRACTICAL_REAL_WORLD_APPLICATION, 0.25,
                CONTEXTUAL_COMPLEXITY, 0.25,
                ORIGINAL_JUDGMENT_CREATIVITY, 0.20)));
        weights.put(UniversityProfileMeasure.TRANSFERABLE_CAPABILITY, measureWeights(Map.of(
                SKILL_TRANSFERABILITY, 0.40,
                CAREER_BREADTH, 0.35,
                ANALYTICAL_REASONING, 0.15,
                CONTEXTUAL_COMPLEXITY, 0.10)));
        return Collections.unmodifiableMap(weights);
    }

    /** Orders a measure's weights by schema order and rejects unknown dimensions or a sum other than 1. */
    static Map<String, Double> measureWeights(Map<String, Double> weights) {
        double total = 0.0;
        for (var weight : weights.entrySet()) {
            if (!SCHEMA_ORDER.contains(weight.getKey())) {
                throw new IllegalStateException("unknown policy dimension: " + weight.getKey());
            }
            if (!(weight.getValue() > 0.0)) {
                throw new IllegalStateException("policy weights must be positive: " + weight.getKey());
            }
            total += weight.getValue();
        }
        if (Math.abs(total - 1.0) > TOLERANCE) {
            throw new IllegalStateException("policy weights must sum to 1");
        }
        LinkedHashMap<String, Double> ordered = new LinkedHashMap<>();
        SCHEMA_ORDER.stream().filter(weights::containsKey).forEach(id -> ordered.put(id, weights.get(id)));
        return Collections.unmodifiableMap(ordered);
    }

    private record Score(double value, double confidence) {
    }

    private record Validated(double informationSufficiency, Map<String, Score> scores) {
        Score score(String dimensionId) {
            return scores.get(dimensionId);
        }
    }
}
