package com.chikere.futureai.decision.alevel;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Deterministic {@code a-level-decision-v2} policy over {@code a-level-v2} judgments of a subject combination.
 *
 * <p>A subject profile with no composite headline score, designed and calibrated offline in Stage A1/A2:</p>
 * <ul>
 *   <li>Options Kept Open: 0.60 discipline family coverage and 0.40 subject skill diversity.</li>
 *   <li>Capability Profile, two sides shown together and never added up: Analytical &amp; Quantitative (0.40
 *       quantitative, 0.40 analytical, 0.20 technical) and Communication, Interpretation &amp; Context (0.40
 *       writing, 0.30 interpretation and originality, 0.30 context).</li>
 *   <li>Learning Alongside AI: 0.70 AI learning support, with one input from each capability side at 0.15 so it
 *       does not become another analytical score.</li>
 * </ul>
 *
 * <p>Thresholds, materiality, factor counts and confidence rules are those of {@code university-decision-v2}. The
 * weights are provisional product policy, not validated educational research; the seven-case live calibration is a
 * release gate. Changing any of them needs a new policy version.</p>
 *
 * <p>The judgments describe the subjects only, never the student. {@link ALevelDecisionPolicyV1} is untouched and
 * keeps deciding the meaning of historical assessments. This class is pure Java: it names no subject and calls no
 * judgment provider, model, persistence or evidence source.</p>
 */
public final class ALevelDecisionPolicyV2 {

    public static final String POLICY_VERSION = "a-level-decision-v2";
    public static final String SUPPORTED_SCHEMA_VERSION = "a-level-v2";

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

    public static final String INFORMATION_SUFFICIENT = "information_sufficient";
    public static final String QUANTITATIVE_REASONING = "quantitative_reasoning";
    public static final String ANALYTICAL_REASONING = "analytical_reasoning";
    public static final String WRITTEN_VERBAL_COMMUNICATION = "written_verbal_communication";
    public static final String ORIGINAL_JUDGMENT_CREATIVITY = "original_judgment_creativity";
    public static final String CONTEXTUAL_SOCIAL_UNDERSTANDING = "contextual_social_understanding";
    public static final String TECHNICAL_DIGITAL_FOUNDATION = "technical_digital_foundation";
    public static final String SUBJECT_SKILL_DIVERSITY = "subject_skill_diversity";
    public static final String DISCIPLINE_FAMILY_COVERAGE = "discipline_family_coverage";
    public static final String AI_LEARNING_SUPPORT = "ai_learning_support";

    /** The nine Score dimensions of {@code a-level-v2}, in schema order. */
    public static final List<String> SCORE_DIMENSIONS = List.of(QUANTITATIVE_REASONING, ANALYTICAL_REASONING,
            WRITTEN_VERBAL_COMMUNICATION, ORIGINAL_JUDGMENT_CREATIVITY, CONTEXTUAL_SOCIAL_UNDERSTANDING,
            TECHNICAL_DIGITAL_FOUNDATION, SUBJECT_SKILL_DIVERSITY, DISCIPLINE_FAMILY_COVERAGE, AI_LEARNING_SUPPORT);

    private static final Map<ALevelProfileMeasure, Map<String, Double>> WEIGHTS = weights();

    private final AssessmentTrace trace;

    public ALevelDecisionPolicyV2() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the values this policy computes; it never changes a decision. */
    public ALevelDecisionPolicyV2(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ALevelProfileDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        Validated judgments = validate(judgmentSet);
        double information = judgments.informationSufficiency();

        if (below(information, INFORMATION_REQUIRED_THRESHOLD)) {
            traceStatus(information, AssessmentStatus.MORE_INFORMATION_REQUIRED, 0);
            return new ALevelProfileDecisionResult(POLICY_VERSION, information,
                    decision("MORE INFORMATION REQUIRED", AssessmentStatus.MORE_INFORMATION_REQUIRED,
                            AssessmentConfidence.LOW), Map.of());
        }

        EnumMap<ALevelProfileMeasure, ALevelProfileMeasureResult> measures = new EnumMap<>(ALevelProfileMeasure.class);
        for (ALevelProfileMeasure measure : ALevelProfileMeasure.values()) {
            measures.put(measure, measure(measure, judgments));
        }
        long lowerConfidence = measures.values().stream().filter(ALevelProfileMeasureResult::lowerConfidence).count();
        AssessmentStatus status = below(information, READY_INFORMATION_THRESHOLD)
                || lowerConfidence >= LOWER_CONFIDENCE_MEASURES_FOR_CAUTION
                ? AssessmentStatus.READY_WITH_CAUTION : AssessmentStatus.READY;
        // Internal and compatibility only, never shown to customers; deliberately stricter than the status, so a
        // single lower-confidence measure already lowers it while the status needs two.
        AssessmentConfidence confidence = below(information, READY_INFORMATION_THRESHOLD) || lowerConfidence > 0
                ? AssessmentConfidence.MODERATE : AssessmentConfidence.HIGH;
        traceStatus(information, status, lowerConfidence);
        return new ALevelProfileDecisionResult(POLICY_VERSION, information,
                decision("SUBJECT PROFILE", status, confidence), measures);
    }

    public static ALevelProfileBand band(double indicator) {
        if (!Double.isFinite(indicator) || indicator < 0.0 || indicator > 10.0) {
            throw new IllegalArgumentException("indicator must be finite and between 0 and 10");
        }
        if (below(indicator, MODERATE_FROM)) {
            return ALevelProfileBand.LOW;
        }
        return below(indicator, HIGH_FROM) ? ALevelProfileBand.MODERATE : ALevelProfileBand.HIGH;
    }

    /** The explicit, testable v2 weights for one measure, in schema order. */
    public static Map<String, Double> weightsFor(ALevelProfileMeasure measure) {
        return WEIGHTS.get(Objects.requireNonNull(measure, "measure must not be null"));
    }

    private ALevelProfileMeasureResult measure(ALevelProfileMeasure measure, Validated judgments) {
        Map<String, Double> weights = WEIGHTS.get(measure);
        List<WeightedContribution> inputs = weights.entrySet().stream().map(weight -> {
            Score score = judgments.score(weight.getKey());
            return WeightedContribution.of(weight.getKey(), score.value(), "normalise: score / 4",
                    score.value() / 4.0, weight.getValue(), score.confidence());
        }).toList();
        double composite = inputs.stream().mapToDouble(WeightedContribution::contribution).sum();
        double confidence = Math.min(1.0, inputs.stream().mapToDouble(WeightedContribution::weightedConfidence).sum());
        double indicator = Math.max(0.0, Math.min(10.0, 10.0 * composite));
        ALevelProfileBand band = band(indicator);

        Comparator<ALevelProfileFactor> largestFirst = Comparator
                .<ALevelProfileFactor>comparingLong(factor -> -Math.round(Math.abs(factor.effect()) / TOLERANCE))
                .thenComparingInt(factor -> SCORE_DIMENSIONS.indexOf(factor.dimensionId()));
        List<ALevelProfileFactor> contributions = inputs.stream()
                .map(input -> new ALevelProfileFactor(input.dimensionId(), input.rawScore(), input.weight(),
                        10.0 * input.weight() * (input.transformedValue() - 0.5)))
                .sorted(largestFirst).toList();
        List<ALevelProfileFactor> drivers = contributions.stream()
                .filter(factor -> factor.effect() > 0.0 && !below(factor.effect(), MATERIALITY))
                .limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        // A lighter capability side is the shape of the combination, not a shortfall, so it never explains limits;
        // nor does any measure in its top band.
        List<ALevelProfileFactor> limits = measure.capabilityProfile() || band == ALevelProfileBand.HIGH
                ? List.of()
                : contributions.stream()
                        .filter(factor -> factor.effect() < 0.0 && !below(-factor.effect(), MATERIALITY))
                        .limit(MAXIMUM_FACTORS_PER_DIRECTION).toList();
        boolean lowerConfidence = below(confidence, LOWER_CONFIDENCE_BELOW);

        trace.event(JourneyType.A_LEVEL, TraceStage.SCORE_CALCULATION, () -> DecisionTracePayloads.measure(
                POLICY_VERSION, measure, "WEIGHTED_COMPOSITE", inputs, composite, indicator, band,
                Map.of(ALevelProfileBand.LOW.name(), "score < " + MODERATE_FROM,
                        ALevelProfileBand.MODERATE.name(), MODERATE_FROM + " <= score < " + HIGH_FROM,
                        ALevelProfileBand.HIGH.name(), "score >= " + HIGH_FROM), confidence));
        trace.event(JourneyType.A_LEVEL, TraceStage.FACTOR_SELECTION, () -> factorTrace(measure, contributions,
                drivers, limits, confidence, lowerConfidence));
        return new ALevelProfileMeasureResult(measure, indicator, band, confidence, lowerConfidence, contributions,
                drivers, limits);
    }

    /** There is no composite headline: the overall indicator stays empty and no headline drivers are named. */
    private static AssessmentDecision decision(String label, AssessmentStatus status,
            AssessmentConfidence confidence) {
        return new AssessmentDecision(JourneyType.A_LEVEL, OptionalDouble.empty(), label, confidence, status,
                List.of(), List.of());
    }

    /** {@code value < threshold}, allowing for floating-point error at the threshold itself. */
    private static boolean below(double value, double threshold) {
        return value < threshold - TOLERANCE;
    }

    private void traceStatus(double information, AssessmentStatus status, long lowerConfidenceMeasures) {
        trace.event(JourneyType.A_LEVEL, TraceStage.INFORMATION_SUFFICIENCY, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("policyVersion", POLICY_VERSION);
            payload.put("informationSufficiency", information);
            payload.put("moreInformationRequiredBelow", INFORMATION_REQUIRED_THRESHOLD);
            payload.put("readyRequiresAtLeast", READY_INFORMATION_THRESHOLD);
            payload.put("lowerConfidenceMeasures", lowerConfidenceMeasures);
            payload.put("cautionWhenLowerConfidenceMeasuresAtLeast", LOWER_CONFIDENCE_MEASURES_FOR_CAUTION);
            payload.put("status", status);
            payload.put("overallIndicator", "none: a subject profile has no composite score");
            return payload;
        });
    }

    private static Map<String, Object> factorTrace(ALevelProfileMeasure measure,
            List<ALevelProfileFactor> contributions, List<ALevelProfileFactor> drivers,
            List<ALevelProfileFactor> limits, double confidence, boolean lowerConfidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyVersion", POLICY_VERSION);
        payload.put("measure", measure);
        payload.put("effectFormula", "10 x weight x (score / 4 - 0.5); effects sum to score - 5");
        payload.put("contributions", contributions);
        payload.put("materialityPoints", MATERIALITY);
        payload.put("drivers", drivers.stream().map(ALevelProfileFactor::dimensionId).toList());
        payload.put("limitingFactors", limits.stream().map(ALevelProfileFactor::dimensionId).toList());
        payload.put("limitsExplained", !measure.capabilityProfile());
        payload.put("measureConfidence", confidence);
        payload.put("lowerConfidence", lowerConfidence);
        return payload;
    }

    /**
     * Exactly the {@code a-level-v2} judgments: information sufficiency and the nine scores, each scored 0–4 with a
     * 0–1 confidence. A missing, repeated, unknown, valueless or out-of-range judgment is rejected rather than
     * ignored.
     */
    private static Validated validate(AssessmentJudgmentSet judgmentSet) {
        Objects.requireNonNull(judgmentSet, "judgmentSet must not be null");
        if (judgmentSet.journey() != JourneyType.A_LEVEL) {
            throw invalid("journey must be A_LEVEL");
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
        Set<String> unexpected = new HashSet<>(byId.keySet());
        unexpected.remove(INFORMATION_SUFFICIENT);
        SCORE_DIMENSIONS.forEach(unexpected::remove);
        if (!unexpected.isEmpty()) {
            throw invalid("unexpected judgments: " + unexpected.stream().sorted().toList());
        }
        double information = numeric(required(byId, INFORMATION_SUFFICIENT));
        requireRange(information, 0.0, 1.0, INFORMATION_SUFFICIENT + " value");
        Map<String, Score> scores = new HashMap<>();
        for (String dimensionId : SCORE_DIMENSIONS) {
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

    private static InvalidALevelJudgmentSetException invalid(String message) {
        return new InvalidALevelJudgmentSetException("Invalid a-level judgment set: " + message);
    }

    private static Map<ALevelProfileMeasure, Map<String, Double>> weights() {
        EnumMap<ALevelProfileMeasure, Map<String, Double>> weights = new EnumMap<>(ALevelProfileMeasure.class);
        weights.put(ALevelProfileMeasure.OPTIONS_KEPT_OPEN, measureWeights(Map.of(
                DISCIPLINE_FAMILY_COVERAGE, 0.60,
                SUBJECT_SKILL_DIVERSITY, 0.40)));
        weights.put(ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE, measureWeights(Map.of(
                QUANTITATIVE_REASONING, 0.40,
                ANALYTICAL_REASONING, 0.40,
                TECHNICAL_DIGITAL_FOUNDATION, 0.20)));
        weights.put(ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT, measureWeights(Map.of(
                WRITTEN_VERBAL_COMMUNICATION, 0.40,
                ORIGINAL_JUDGMENT_CREATIVITY, 0.30,
                CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.30)));
        weights.put(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI, measureWeights(Map.of(
                AI_LEARNING_SUPPORT, 0.70,
                ANALYTICAL_REASONING, 0.15,
                ORIGINAL_JUDGMENT_CREATIVITY, 0.15)));
        return Collections.unmodifiableMap(weights);
    }

    /** Orders a measure's weights by schema order and rejects unknown dimensions or a sum other than 1. */
    static Map<String, Double> measureWeights(Map<String, Double> weights) {
        double total = 0.0;
        for (var weight : weights.entrySet()) {
            if (!SCORE_DIMENSIONS.contains(weight.getKey())) {
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
        SCORE_DIMENSIONS.stream().filter(weights::containsKey).forEach(id -> ordered.put(id, weights.get(id)));
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
