package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.DecisionTracePayloads;
import com.chikere.futureai.decision.FactorCandidate;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Deterministic product policy for {@code university-v1} judgments.
 *
 * <p>These weights and thresholds are transparent, provisional V1 product policy. They are not
 * claimed to be scientifically validated truths about education or employment. Changing them
 * requires a new policy version so an existing version never silently changes meaning.</p>
 *
 * <p>The university measures are not the professional measures and share no scale semantics with
 * them. Future Readiness is not the inverse of professional AI Exposure, and the two must never be
 * compared numerically.</p>
 *
 * <p>This class is pure Java. It calls no judgment provider, no model, no persistence and no
 * evidence source.</p>
 */
public final class UniversityDecisionPolicyV1 {

    public static final String POLICY_VERSION = "university-decision-v1";

    private static final String SCHEMA_VERSION = "university-v1";
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

    /** The nine Score dimensions, in schema order. */
    public static final List<String> SCORE_DIMENSIONS = List.of(
            TECHNICAL_DIGITAL_DEPTH,
            ANALYTICAL_REASONING,
            ORIGINAL_JUDGMENT_CREATIVITY,
            HUMAN_RELATIONSHIP_DEPENDENCY,
            CONTEXTUAL_COMPLEXITY,
            PRACTICAL_REAL_WORLD_APPLICATION,
            AI_COMPLEMENTARITY,
            SKILL_TRANSFERABILITY,
            CAREER_BREADTH);

    private static final double INFORMATION_REQUIRED_THRESHOLD = 0.60;
    private static final double READY_INFORMATION_THRESHOLD = 0.75;
    private static final double HIGH_INFORMATION_THRESHOLD = 0.80;
    private static final double MODERATE_MEAN_CONFIDENCE_THRESHOLD = 0.65;
    private static final double HIGH_MEAN_CONFIDENCE_THRESHOLD = 0.80;
    private static final double HIGH_MINIMUM_CONFIDENCE_THRESHOLD = 0.60;
    private static final double DEVELOPING_BAND_THRESHOLD = 3.50;
    private static final double STRONG_BAND_THRESHOLD = 7.00;
    private static final int MAXIMUM_DISPLAYED_FACTORS = 3;
    private static final Map<String, String> BAND_THRESHOLDS = bandThresholds();

    private static final Map<String, WeightedDimension> FUTURE_READINESS_WEIGHTS = weightedDimensions(
            new WeightedDimension(ANALYTICAL_REASONING, 0.20),
            new WeightedDimension(TECHNICAL_DIGITAL_DEPTH, 0.15),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.15),
            new WeightedDimension(AI_COMPLEMENTARITY, 0.15),
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.15),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.10),
            new WeightedDimension(CAREER_BREADTH, 0.10));

    private static final Map<String, WeightedDimension> HUMAN_DIFFERENTIATION_WEIGHTS = weightedDimensions(
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.30),
            new WeightedDimension(HUMAN_RELATIONSHIP_DEPENDENCY, 0.30),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.20),
            new WeightedDimension(PRACTICAL_REAL_WORLD_APPLICATION, 0.20));

    private static final Map<String, WeightedDimension> AI_COMPLEMENTARITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(AI_COMPLEMENTARITY, 0.70),
            new WeightedDimension(TECHNICAL_DIGITAL_DEPTH, 0.15),
            new WeightedDimension(ANALYTICAL_REASONING, 0.15));

    private static final Map<String, WeightedDimension> SKILL_TRANSFERABILITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.60),
            new WeightedDimension(ANALYTICAL_REASONING, 0.20),
            new WeightedDimension(CAREER_BREADTH, 0.20));

    private static final Map<String, WeightedDimension> CAREER_ADAPTABILITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.25),
            new WeightedDimension(CAREER_BREADTH, 0.25),
            new WeightedDimension(ANALYTICAL_REASONING, 0.20),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.15),
            new WeightedDimension(AI_COMPLEMENTARITY, 0.15));

    /**
     * Explanation-only weights covering all nine Score dimensions.
     *
     * <p>They select and order strengths and development opportunities so that human and practical
     * dimensions can be explained even though they do not enter Future Readiness. They never
     * contribute to an authoritative measure.</p>
     */
    private static final Map<String, WeightedDimension> EXPLANATION_WEIGHTS = weightedDimensions(
            new WeightedDimension(ANALYTICAL_REASONING, 0.15),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.13),
            new WeightedDimension(TECHNICAL_DIGITAL_DEPTH, 0.12),
            new WeightedDimension(AI_COMPLEMENTARITY, 0.12),
            new WeightedDimension(HUMAN_RELATIONSHIP_DEPENDENCY, 0.10),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.10),
            new WeightedDimension(PRACTICAL_REAL_WORLD_APPLICATION, 0.10),
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.10),
            new WeightedDimension(CAREER_BREADTH, 0.08));

    private final AssessmentTrace trace;

    public UniversityDecisionPolicyV1() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the values this policy computes; it never changes a decision. */
    public UniversityDecisionPolicyV1(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        ValidatedJudgments judgments = validate(judgmentSet);
        double informationSufficiency = judgments.informationSufficiency();

        if (informationSufficiency < INFORMATION_REQUIRED_THRESHOLD) {
            traceInformationSufficiency(informationSufficiency, AssessmentStatus.MORE_INFORMATION_REQUIRED,
                    AssessmentConfidence.LOW, false);
            AssessmentDecision headline = new AssessmentDecision(
                    JourneyType.UNIVERSITY,
                    OptionalDouble.empty(),
                    "MORE INFORMATION REQUIRED",
                    AssessmentConfidence.LOW,
                    AssessmentStatus.MORE_INFORMATION_REQUIRED,
                    List.of(),
                    List.of());
            return new UniversityDecisionResult(
                    POLICY_VERSION, informationSufficiency, headline, Map.of(), List.of(), List.of());
        }

        ConfidenceAssessment confidenceAssessment = assessmentConfidence(judgments);
        AssessmentConfidence assessmentConfidence = confidenceAssessment.confidence();
        AssessmentStatus status = assessmentStatus(informationSufficiency, assessmentConfidence);
        traceInformationSufficiency(informationSufficiency, status, assessmentConfidence, true);
        traceConfidence(informationSufficiency, confidenceAssessment);
        EnumMap<UniversityMeasure, UniversityMeasureResult> measures = calculateMeasures(judgments);
        UniversityMeasureResult futureReadiness = measures.get(UniversityMeasure.FUTURE_READINESS);

        List<FactorCandidate> candidates = explanationCandidates(judgments);
        List<UniversityDecisionFactor> factors = explanationFactors(candidates);
        List<UniversityDecisionFactor> rankedStrengths = factors.stream()
                .filter(factor -> factor.signedEffect() > 0.0)
                .sorted(Comparator.comparingDouble(UniversityDecisionFactor::signedEffect).reversed())
                .toList();
        List<UniversityDecisionFactor> strengths = rankedStrengths.stream()
                .limit(MAXIMUM_DISPLAYED_FACTORS)
                .toList();
        List<UniversityDecisionFactor> rankedOpportunities = factors.stream()
                .filter(factor -> factor.signedEffect() < 0.0)
                .sorted(Comparator.comparingDouble(UniversityDecisionFactor::signedEffect))
                .toList();
        List<UniversityDecisionFactor> opportunities = rankedOpportunities.stream()
                .limit(MAXIMUM_DISPLAYED_FACTORS)
                .toList();
        trace.event(JourneyType.UNIVERSITY, TraceStage.FACTOR_SELECTION, () -> DecisionTracePayloads.factorSelection(
                POLICY_VERSION, "explanation-only weights (never part of a measure)", candidates,
                rankedStrengths.stream().map(UniversityDecisionFactor::dimensionId).toList(),
                rankedOpportunities.stream().map(UniversityDecisionFactor::dimensionId).toList(),
                MAXIMUM_DISPLAYED_FACTORS, "strengths", "developmentOpportunities"));

        AssessmentDecision headline = new AssessmentDecision(
                JourneyType.UNIVERSITY,
                OptionalDouble.of(futureReadiness.indicator()),
                futureReadiness.band().name() + " FUTURE READINESS",
                assessmentConfidence,
                status,
                strengths.stream().map(UniversityDecisionFactor::dimensionId).toList(),
                opportunities.stream().map(UniversityDecisionFactor::dimensionId).toList());
        return new UniversityDecisionResult(
                POLICY_VERSION, informationSufficiency, headline, measures, strengths, opportunities);
    }

    public static ReadinessBand readinessBand(double indicator) {
        if (!Double.isFinite(indicator) || indicator < 0.0 || indicator > 10.0) {
            throw new IllegalArgumentException("indicator must be finite and between 0 and 10");
        }
        if (indicator < DEVELOPING_BAND_THRESHOLD) {
            return ReadinessBand.LIMITED;
        }
        if (indicator < STRONG_BAND_THRESHOLD) {
            return ReadinessBand.DEVELOPING;
        }
        return ReadinessBand.STRONG;
    }

    /** The explicit, testable V1 weights for one measure. */
    public static Map<String, Double> weightsFor(UniversityMeasure measure) {
        return weightsOf(measure).entrySet().stream().collect(LinkedHashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue().weight()), LinkedHashMap::putAll);
    }

    /** The explicit, testable V1 explanation weights. They never affect an authoritative measure. */
    public static Map<String, Double> explanationWeights() {
        return EXPLANATION_WEIGHTS.entrySet().stream().collect(LinkedHashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue().weight()), LinkedHashMap::putAll);
    }

    private static Map<String, WeightedDimension> weightsOf(UniversityMeasure measure) {
        return switch (measure) {
            case FUTURE_READINESS -> FUTURE_READINESS_WEIGHTS;
            case HUMAN_DIFFERENTIATION -> HUMAN_DIFFERENTIATION_WEIGHTS;
            case AI_COMPLEMENTARITY -> AI_COMPLEMENTARITY_WEIGHTS;
            case SKILL_TRANSFERABILITY -> SKILL_TRANSFERABILITY_WEIGHTS;
            case CAREER_ADAPTABILITY -> CAREER_ADAPTABILITY_WEIGHTS;
        };
    }

    private ValidatedJudgments validate(AssessmentJudgmentSet judgmentSet) {
        Objects.requireNonNull(judgmentSet, "judgmentSet must not be null");
        if (judgmentSet.journey() != JourneyType.UNIVERSITY) {
            throw invalid("journey must be UNIVERSITY");
        }
        if (!SCHEMA_VERSION.equals(judgmentSet.schemaVersion())) {
            throw invalid("schemaVersion must be " + SCHEMA_VERSION);
        }

        Map<String, DimensionJudgment> byId = new HashMap<>();
        for (DimensionJudgment judgment : judgmentSet.judgments()) {
            DimensionJudgment previous = byId.put(judgment.dimensionId(), judgment);
            if (previous != null) {
                throw invalid("duplicate judgment: " + judgment.dimensionId());
            }
        }

        DimensionJudgment information = requiredJudgment(byId, INFORMATION_SUFFICIENT);
        double informationValue = requiredNumericValue(information);
        requireRange(informationValue, 0.0, 1.0, INFORMATION_SUFFICIENT + " value");

        LinkedHashMap<String, ScoreValue> scores = new LinkedHashMap<>();
        for (String dimensionId : SCORE_DIMENSIONS) {
            DimensionJudgment judgment = requiredJudgment(byId, dimensionId);
            double score = requiredNumericValue(judgment);
            requireRange(score, 0.0, 4.0, dimensionId + " score");
            if (judgment.confidenceValue().isEmpty()) {
                throw invalid("missing confidence value for " + dimensionId);
            }
            double confidence = judgment.confidenceValue().getAsDouble();
            requireRange(confidence, 0.0, 1.0, dimensionId + " confidence");
            scores.put(dimensionId, new ScoreValue(score, confidence));
        }
        return new ValidatedJudgments(informationValue, Map.copyOf(scores));
    }

    private EnumMap<UniversityMeasure, UniversityMeasureResult> calculateMeasures(ValidatedJudgments judgments) {
        EnumMap<UniversityMeasure, UniversityMeasureResult> results = new EnumMap<>(UniversityMeasure.class);
        for (UniversityMeasure measure : UniversityMeasure.values()) {
            results.put(measure, compositeMeasure(measure, weightsOf(measure), judgments));
        }
        return results;
    }

    private UniversityMeasureResult compositeMeasure(
            UniversityMeasure measure, Map<String, WeightedDimension> weights, ValidatedJudgments judgments) {
        List<WeightedContribution> contributions = weights.values().stream()
                .map(weighted -> {
                    ScoreValue score = judgments.score(weighted.dimensionId());
                    return WeightedContribution.of(weighted.dimensionId(), score.score(), "normalise: score / 4",
                            UniversityScoreScale.normalise(score.score()), weighted.weight(), score.confidence());
                })
                .toList();
        double composite = contributions.stream().mapToDouble(WeightedContribution::contribution).sum();
        double confidence = contributions.stream().mapToDouble(WeightedContribution::weightedConfidence).sum();
        double indicator = UniversityScoreScale.toIndicator(composite);
        UniversityMeasureResult result = new UniversityMeasureResult(measure, indicator, readinessBand(indicator),
                confidence);
        trace.event(JourneyType.UNIVERSITY, TraceStage.SCORE_CALCULATION, () -> DecisionTracePayloads.measure(
                POLICY_VERSION, measure, "WEIGHTED_COMPOSITE", contributions, composite, result.indicator(),
                result.band(), BAND_THRESHOLDS, result.confidence()));
        return result;
    }

    private void traceInformationSufficiency(double informationSufficiency, AssessmentStatus status,
            AssessmentConfidence confidence, boolean scoresProduced) {
        trace.event(JourneyType.UNIVERSITY, TraceStage.INFORMATION_SUFFICIENCY,
                () -> DecisionTracePayloads.informationSufficiency(POLICY_VERSION, informationSufficiency,
                        INFORMATION_REQUIRED_THRESHOLD, READY_INFORMATION_THRESHOLD, status, confidence,
                        scoresProduced));
    }

    private void traceConfidence(double informationSufficiency, ConfidenceAssessment assessment) {
        trace.event(JourneyType.UNIVERSITY, TraceStage.ASSESSMENT_CONFIDENCE, () -> DecisionTracePayloads.confidence(
                POLICY_VERSION, informationSufficiency, assessment.mean(), assessment.minimum(), Map.of(
                        "highRequiresInformationAtLeast", HIGH_INFORMATION_THRESHOLD,
                        "highRequiresMeanAtLeast", HIGH_MEAN_CONFIDENCE_THRESHOLD,
                        "highRequiresMinimumAtLeast", HIGH_MINIMUM_CONFIDENCE_THRESHOLD,
                        "moderateRequiresInformationAtLeast", INFORMATION_REQUIRED_THRESHOLD,
                        "moderateRequiresMeanAtLeast", MODERATE_MEAN_CONFIDENCE_THRESHOLD),
                assessment.confidence()));
    }

    private static Map<String, String> bandThresholds() {
        LinkedHashMap<String, String> thresholds = new LinkedHashMap<>();
        thresholds.put(ReadinessBand.LIMITED.name(), "score < " + DEVELOPING_BAND_THRESHOLD);
        thresholds.put(ReadinessBand.DEVELOPING.name(),
                DEVELOPING_BAND_THRESHOLD + " <= score < " + STRONG_BAND_THRESHOLD);
        thresholds.put(ReadinessBand.STRONG.name(), "score >= " + STRONG_BAND_THRESHOLD);
        return Collections.unmodifiableMap(thresholds);
    }

    private ConfidenceAssessment assessmentConfidence(ValidatedJudgments judgments) {
        double mean = SCORE_DIMENSIONS.stream()
                .map(judgments::score).mapToDouble(ScoreValue::confidence).average().orElseThrow();
        double minimum = SCORE_DIMENSIONS.stream()
                .map(judgments::score).mapToDouble(ScoreValue::confidence).min().orElseThrow();

        if (judgments.informationSufficiency() >= HIGH_INFORMATION_THRESHOLD
                && mean >= HIGH_MEAN_CONFIDENCE_THRESHOLD
                && minimum >= HIGH_MINIMUM_CONFIDENCE_THRESHOLD) {
            return new ConfidenceAssessment(mean, minimum, AssessmentConfidence.HIGH);
        }
        if (judgments.informationSufficiency() >= INFORMATION_REQUIRED_THRESHOLD
                && mean >= MODERATE_MEAN_CONFIDENCE_THRESHOLD) {
            return new ConfidenceAssessment(mean, minimum, AssessmentConfidence.MODERATE);
        }
        return new ConfidenceAssessment(mean, minimum, AssessmentConfidence.LOW);
    }

    private AssessmentStatus assessmentStatus(double informationSufficiency, AssessmentConfidence confidence) {
        if (informationSufficiency < READY_INFORMATION_THRESHOLD || confidence != AssessmentConfidence.HIGH) {
            return AssessmentStatus.READY_WITH_CAUTION;
        }
        return AssessmentStatus.READY;
    }

    private List<FactorCandidate> explanationCandidates(ValidatedJudgments judgments) {
        return EXPLANATION_WEIGHTS.values().stream().map(weighted -> {
            ScoreValue score = judgments.score(weighted.dimensionId());
            double readinessComponent = UniversityScoreScale.normalise(score.score());
            double signedEffect = weighted.weight() * (readinessComponent - 0.5);
            return new FactorCandidate(weighted.dimensionId(), score.score(), readinessComponent,
                    weighted.weight(), signedEffect);
        }).toList();
    }

    private List<UniversityDecisionFactor> explanationFactors(List<FactorCandidate> candidates) {
        return candidates.stream().map(candidate -> {
            double signedEffect = candidate.signedEffect();
            if (signedEffect == 0.0) {
                return null;
            }
            return new UniversityDecisionFactor(
                    candidate.dimensionId(),
                    signedEffect > 0.0
                            ? UniversityFactorEffect.STRENGTH
                            : UniversityFactorEffect.DEVELOPMENT_OPPORTUNITY,
                    UniversityFactorDirection.valueOf((candidate.rawScore() > 2.0 ? "HIGH_" : "LOW_")
                            + candidate.dimensionId().toUpperCase(Locale.ROOT)),
                    candidate.rawScore(), candidate.transformedValue(), candidate.weight(), signedEffect);
        }).filter(Objects::nonNull).toList();
    }

    private static Map<String, WeightedDimension> weightedDimensions(WeightedDimension... dimensions) {
        LinkedHashMap<String, WeightedDimension> weights = new LinkedHashMap<>();
        double total = 0.0;
        for (WeightedDimension dimension : dimensions) {
            if (weights.put(dimension.dimensionId(), dimension) != null) {
                throw new IllegalStateException("duplicate policy dimension: " + dimension.dimensionId());
            }
            if (!SCORE_DIMENSIONS.contains(dimension.dimensionId())) {
                throw new IllegalStateException("unknown policy dimension: " + dimension.dimensionId());
            }
            total += dimension.weight();
        }
        if (Math.abs(total - 1.0) > 1.0e-9) {
            throw new IllegalStateException("policy weights must sum to 1");
        }
        return Collections.unmodifiableMap(weights);
    }

    private DimensionJudgment requiredJudgment(Map<String, DimensionJudgment> judgments, String dimensionId) {
        DimensionJudgment judgment = judgments.get(dimensionId);
        if (judgment == null) {
            throw invalid("missing required judgment: " + dimensionId);
        }
        return judgment;
    }

    private double requiredNumericValue(DimensionJudgment judgment) {
        if (judgment.numericValue().isEmpty()) {
            throw invalid("missing numeric value for " + judgment.dimensionId());
        }
        return judgment.numericValue().getAsDouble();
    }

    private void requireRange(double value, double minimum, double maximum, String description) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw invalid(description + " must be between " + minimum + " and " + maximum);
        }
    }

    private InvalidUniversityJudgmentSetException invalid(String message) {
        return new InvalidUniversityJudgmentSetException("Invalid university judgment set: " + message);
    }

    private record ScoreValue(double score, double confidence) {
    }

    private record ConfidenceAssessment(double mean, double minimum, AssessmentConfidence confidence) {
    }

    private record ValidatedJudgments(double informationSufficiency, Map<String, ScoreValue> scores) {
        ScoreValue score(String dimensionId) {
            return scores.get(dimensionId);
        }
    }

    private record WeightedDimension(String dimensionId, double weight) {
    }
}
