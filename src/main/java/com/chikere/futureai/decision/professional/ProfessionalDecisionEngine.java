package com.chikere.futureai.decision.professional;

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

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * The deterministic calculation shared by every Professional decision policy version for professional-v1
 * judgments.
 *
 * <p>The weights and thresholds in this class are transparent product policy. They are not claimed to be
 * scientifically validated universal truths. Changing them requires a new policy version. The versions
 * differ only in which AI Exposure factors are material enough to select: see
 * {@link ProfessionalDecisionPolicyV1} and {@link ProfessionalDecisionPolicyV2}.</p>
 */
final class ProfessionalDecisionEngine {

    /**
     * Absolute tolerance for the materiality comparison, so an effect mathematically equal to the threshold
     * but represented a few ulps below it (for example 0.1 x 0.15 = 0.015 - 3e-18) is still retained. It is
     * many orders of magnitude below any difference the threshold is meant to distinguish.
     */
    static final double MATERIALITY_TOLERANCE = 1.0e-9;

    private static final String SCHEMA_VERSION = "professional-v1";
    private static final String INFORMATION_SUFFICIENT = "information_sufficient";
    private static final String TASK_REPEATABILITY = "task_repeatability";
    private static final String DIGITAL_EXECUTABILITY = "digital_executability";
    private static final String HUMAN_TRUST_DEPENDENCY = "human_trust_dependency";
    private static final String PHYSICAL_WORLD_DEPENDENCY = "physical_world_dependency";
    private static final String CONTEXTUAL_COMPLEXITY = "contextual_complexity";
    private static final String ORIGINAL_JUDGMENT = "original_judgment";
    private static final String ACCOUNTABILITY_RESPONSIBILITY = "accountability_responsibility";
    private static final String AI_COMPLEMENTARITY = "ai_complementarity";

    private static final List<String> SCORE_DIMENSIONS = List.of(
            TASK_REPEATABILITY,
            DIGITAL_EXECUTABILITY,
            HUMAN_TRUST_DEPENDENCY,
            PHYSICAL_WORLD_DEPENDENCY,
            CONTEXTUAL_COMPLEXITY,
            ORIGINAL_JUDGMENT,
            ACCOUNTABILITY_RESPONSIBILITY,
            AI_COMPLEMENTARITY);

    private static final double INFORMATION_REQUIRED_THRESHOLD = 0.60;
    private static final double READY_INFORMATION_THRESHOLD = 0.75;
    private static final double HIGH_INFORMATION_THRESHOLD = 0.80;
    private static final double MODERATE_MEAN_CONFIDENCE_THRESHOLD = 0.65;
    private static final double HIGH_MEAN_CONFIDENCE_THRESHOLD = 0.80;
    private static final double HIGH_MINIMUM_CONFIDENCE_THRESHOLD = 0.60;
    private static final double MODERATE_BAND_THRESHOLD = ProfessionalBandThresholds.MODERATE_FROM;
    private static final double HIGH_BAND_THRESHOLD = ProfessionalBandThresholds.HIGH_FROM;
    private static final int MAXIMUM_DISPLAYED_FACTORS = 3;
    private static final Map<String, String> BAND_THRESHOLDS = bandThresholds();

    private static final Map<String, WeightedDimension> EXPOSURE_WEIGHTS = weightedDimensions(
            new WeightedDimension(TASK_REPEATABILITY, 0.25, false),
            new WeightedDimension(DIGITAL_EXECUTABILITY, 0.25, false),
            new WeightedDimension(HUMAN_TRUST_DEPENDENCY, 0.15, true),
            new WeightedDimension(PHYSICAL_WORLD_DEPENDENCY, 0.15, true),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.10, true),
            new WeightedDimension(ORIGINAL_JUDGMENT, 0.07, true),
            new WeightedDimension(ACCOUNTABILITY_RESPONSIBILITY, 0.03, true));

    private static final Map<String, WeightedDimension> HUMAN_ADVANTAGE_WEIGHTS = weightedDimensions(
            new WeightedDimension(HUMAN_TRUST_DEPENDENCY, 0.25, false),
            new WeightedDimension(PHYSICAL_WORLD_DEPENDENCY, 0.20, false),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.20, false),
            new WeightedDimension(ORIGINAL_JUDGMENT, 0.20, false),
            new WeightedDimension(ACCOUNTABILITY_RESPONSIBILITY, 0.15, false));

    private static final Map<String, WeightedDimension> ROLE_ADAPTABILITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(AI_COMPLEMENTARITY, 0.35, false),
            new WeightedDimension(CONTEXTUAL_COMPLEXITY, 0.20, false),
            new WeightedDimension(ORIGINAL_JUDGMENT, 0.20, false),
            new WeightedDimension(HUMAN_TRUST_DEPENDENCY, 0.10, false),
            new WeightedDimension(ACCOUNTABILITY_RESPONSIBILITY, 0.10, false),
            new WeightedDimension(TASK_REPEATABILITY, 0.05, true));

    private final String policyVersion;
    private final OptionalDouble materialityThreshold;
    private final AssessmentTrace trace;

    /**
     * @param materialityThreshold the minimum absolute signed effect a factor needs to be selected, or empty
     *                             to select any non-zero factor
     * @param trace                only observes the values this policy computes; it never changes a decision
     */
    ProfessionalDecisionEngine(String policyVersion, OptionalDouble materialityThreshold, AssessmentTrace trace) {
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        this.materialityThreshold = Objects.requireNonNull(materialityThreshold, "materialityThreshold must not be null");
        materialityThreshold.ifPresent(threshold -> {
            if (!Double.isFinite(threshold) || threshold <= 0.0) {
                throw new IllegalArgumentException("materialityThreshold must be positive");
            }
        });
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    String policyVersion() {
        return policyVersion;
    }

    ProfessionalDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        ValidatedJudgments judgments = validate(judgmentSet);
        double informationSufficiency = judgments.informationSufficiency();

        if (informationSufficiency < INFORMATION_REQUIRED_THRESHOLD) {
            traceInformationSufficiency(informationSufficiency, AssessmentStatus.MORE_INFORMATION_REQUIRED,
                    AssessmentConfidence.LOW, false);
            AssessmentDecision headline = new AssessmentDecision(
                    JourneyType.PROFESSIONAL,
                    OptionalDouble.empty(),
                    "MORE INFORMATION REQUIRED",
                    AssessmentConfidence.LOW,
                    AssessmentStatus.MORE_INFORMATION_REQUIRED,
                    List.of(),
                    List.of());
            return new ProfessionalDecisionResult(
                    policyVersion,
                    informationSufficiency,
                    headline,
                    Map.of(),
                    List.of(),
                    List.of());
        }

        ConfidenceAssessment confidenceAssessment = assessmentConfidence(judgments);
        AssessmentConfidence assessmentConfidence = confidenceAssessment.confidence();
        AssessmentStatus status = assessmentStatus(informationSufficiency, assessmentConfidence);
        traceInformationSufficiency(informationSufficiency, status, assessmentConfidence, true);
        traceConfidence(informationSufficiency, confidenceAssessment);
        EnumMap<ProfessionalMeasure, ProfessionalMeasureResult> measures = calculateMeasures(judgments);
        ProfessionalMeasureResult exposure = measures.get(ProfessionalMeasure.AI_EXPOSURE);
        List<FactorCandidate> candidates = exposureCandidates(judgments);
        // Every candidate still contributes to the AI Exposure score above; materiality only decides which
        // factors are significant enough to select and present.
        List<ProfessionalDecisionFactor> factors = exposureFactors(candidates).stream()
                .filter(factor -> isMaterial(factor.signedEffect()))
                .toList();
        List<ProfessionalDecisionFactor> rankedDrivers = factors.stream()
                .filter(factor -> factor.signedEffect() > 0.0)
                .sorted(Comparator.comparingDouble(ProfessionalDecisionFactor::signedEffect).reversed())
                .toList();
        List<ProfessionalDecisionFactor> drivers = rankedDrivers.stream()
                .limit(MAXIMUM_DISPLAYED_FACTORS)
                .toList();
        List<ProfessionalDecisionFactor> rankedProtective = factors.stream()
                .filter(factor -> factor.signedEffect() < 0.0)
                .sorted(Comparator.comparingDouble(ProfessionalDecisionFactor::signedEffect))
                .toList();
        List<ProfessionalDecisionFactor> protectiveFactors = rankedProtective.stream()
                .limit(MAXIMUM_DISPLAYED_FACTORS)
                .toList();
        trace.event(JourneyType.PROFESSIONAL, TraceStage.FACTOR_SELECTION, () -> DecisionTracePayloads.factorSelection(
                policyVersion, "AI Exposure weights", candidates,
                rankedDrivers.stream().map(ProfessionalDecisionFactor::dimensionId).toList(),
                rankedProtective.stream().map(ProfessionalDecisionFactor::dimensionId).toList(),
                MAXIMUM_DISPLAYED_FACTORS, "whatIncreasesAiExposure", "whereHumanSkillsRemainImportant",
                materialityThreshold));

        AssessmentDecision headline = new AssessmentDecision(
                JourneyType.PROFESSIONAL,
                OptionalDouble.of(exposure.indicator()),
                exposure.band().name() + " EXPOSURE",
                assessmentConfidence,
                status,
                drivers.stream().map(ProfessionalDecisionFactor::dimensionId).toList(),
                protectiveFactors.stream().map(ProfessionalDecisionFactor::dimensionId).toList());
        return new ProfessionalDecisionResult(
                policyVersion,
                informationSufficiency,
                headline,
                measures,
                drivers,
                protectiveFactors);
    }

    static IndicatorBand indicatorBand(double indicator) {
        if (!Double.isFinite(indicator) || indicator < 0.0 || indicator > 10.0) {
            throw new IllegalArgumentException("indicator must be finite and between 0 and 10");
        }
        if (indicator < MODERATE_BAND_THRESHOLD) {
            return IndicatorBand.LOW;
        }
        if (indicator < HIGH_BAND_THRESHOLD) {
            return IndicatorBand.MODERATE;
        }
        return IndicatorBand.HIGH;
    }

    private ValidatedJudgments validate(AssessmentJudgmentSet judgmentSet) {
        Objects.requireNonNull(judgmentSet, "judgmentSet must not be null");
        if (judgmentSet.journey() != JourneyType.PROFESSIONAL) {
            throw invalid("journey must be PROFESSIONAL");
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

    private EnumMap<ProfessionalMeasure, ProfessionalMeasureResult> calculateMeasures(ValidatedJudgments judgments) {
        EnumMap<ProfessionalMeasure, ProfessionalMeasureResult> results =
                new EnumMap<>(ProfessionalMeasure.class);
        results.put(
                ProfessionalMeasure.AI_EXPOSURE,
                compositeMeasure(ProfessionalMeasure.AI_EXPOSURE, EXPOSURE_WEIGHTS, judgments));
        results.put(
                ProfessionalMeasure.HUMAN_ADVANTAGE,
                compositeMeasure(ProfessionalMeasure.HUMAN_ADVANTAGE, HUMAN_ADVANTAGE_WEIGHTS, judgments));

        ScoreValue complementarity = judgments.score(AI_COMPLEMENTARITY);
        double normalisedComplementarity = ProfessionalScoreScale.normalise(complementarity.score());
        double complementarityIndicator = ProfessionalScoreScale.toIndicator(normalisedComplementarity);
        ProfessionalMeasureResult complementarityResult = new ProfessionalMeasureResult(
                ProfessionalMeasure.AI_COMPLEMENTARITY,
                complementarityIndicator,
                indicatorBand(complementarityIndicator),
                complementarity.confidence());
        results.put(ProfessionalMeasure.AI_COMPLEMENTARITY, complementarityResult);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.SCORE_CALCULATION, () -> DecisionTracePayloads.measure(
                policyVersion, ProfessionalMeasure.AI_COMPLEMENTARITY,
                "DIRECT: the single ai_complementarity judgment, normalised",
                List.of(WeightedContribution.of(AI_COMPLEMENTARITY, complementarity.score(), "normalise: score / 4",
                        normalisedComplementarity, 1.0, complementarity.confidence())),
                normalisedComplementarity, complementarityResult.indicator(), complementarityResult.band(),
                BAND_THRESHOLDS, complementarityResult.confidence()));
        results.put(
                ProfessionalMeasure.ROLE_ADAPTABILITY,
                compositeMeasure(ProfessionalMeasure.ROLE_ADAPTABILITY, ROLE_ADAPTABILITY_WEIGHTS, judgments));
        return results;
    }

    private ProfessionalMeasureResult compositeMeasure(
            ProfessionalMeasure measure,
            Map<String, WeightedDimension> weights,
            ValidatedJudgments judgments) {
        List<WeightedContribution> contributions = weights.values().stream()
                .map(weighted -> {
                    ScoreValue score = judgments.score(weighted.dimensionId());
                    return WeightedContribution.of(weighted.dimensionId(), score.score(), weighted.transformation(),
                            weighted.value(score), weighted.weight(), score.confidence());
                })
                .toList();
        double composite = contributions.stream().mapToDouble(WeightedContribution::contribution).sum();
        double confidence = contributions.stream().mapToDouble(WeightedContribution::weightedConfidence).sum();
        double indicator = ProfessionalScoreScale.toIndicator(composite);
        ProfessionalMeasureResult result =
                new ProfessionalMeasureResult(measure, indicator, indicatorBand(indicator), confidence);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.SCORE_CALCULATION, () -> DecisionTracePayloads.measure(
                policyVersion, measure, "WEIGHTED_COMPOSITE", contributions, composite, result.indicator(),
                result.band(), BAND_THRESHOLDS, result.confidence()));
        return result;
    }

    private void traceInformationSufficiency(double informationSufficiency, AssessmentStatus status,
            AssessmentConfidence confidence, boolean scoresProduced) {
        trace.event(JourneyType.PROFESSIONAL, TraceStage.INFORMATION_SUFFICIENCY,
                () -> DecisionTracePayloads.informationSufficiency(policyVersion, informationSufficiency,
                        INFORMATION_REQUIRED_THRESHOLD, READY_INFORMATION_THRESHOLD, status, confidence,
                        scoresProduced));
    }

    private void traceConfidence(double informationSufficiency, ConfidenceAssessment assessment) {
        trace.event(JourneyType.PROFESSIONAL, TraceStage.ASSESSMENT_CONFIDENCE, () -> DecisionTracePayloads.confidence(
                policyVersion, informationSufficiency, assessment.mean(), assessment.minimum(), Map.of(
                        "highRequiresInformationAtLeast", HIGH_INFORMATION_THRESHOLD,
                        "highRequiresMeanAtLeast", HIGH_MEAN_CONFIDENCE_THRESHOLD,
                        "highRequiresMinimumAtLeast", HIGH_MINIMUM_CONFIDENCE_THRESHOLD,
                        "moderateRequiresInformationAtLeast", INFORMATION_REQUIRED_THRESHOLD,
                        "moderateRequiresMeanAtLeast", MODERATE_MEAN_CONFIDENCE_THRESHOLD),
                assessment.confidence()));
    }

    private static Map<String, String> bandThresholds() {
        LinkedHashMap<String, String> thresholds = new LinkedHashMap<>();
        thresholds.put(IndicatorBand.LOW.name(), "score < " + MODERATE_BAND_THRESHOLD);
        thresholds.put(IndicatorBand.MODERATE.name(), MODERATE_BAND_THRESHOLD + " <= score < " + HIGH_BAND_THRESHOLD);
        thresholds.put(IndicatorBand.HIGH.name(), "score >= " + HIGH_BAND_THRESHOLD);
        return java.util.Collections.unmodifiableMap(thresholds);
    }

    private ConfidenceAssessment assessmentConfidence(ValidatedJudgments judgments) {
        double mean = SCORE_DIMENSIONS.stream()
                .map(judgments::score)
                .mapToDouble(ScoreValue::confidence)
                .average()
                .orElseThrow();
        double minimum = SCORE_DIMENSIONS.stream()
                .map(judgments::score)
                .mapToDouble(ScoreValue::confidence)
                .min()
                .orElseThrow();

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

    private AssessmentStatus assessmentStatus(
            double informationSufficiency,
            AssessmentConfidence confidence) {
        if (informationSufficiency < READY_INFORMATION_THRESHOLD || confidence != AssessmentConfidence.HIGH) {
            return AssessmentStatus.READY_WITH_CAUTION;
        }
        return AssessmentStatus.READY;
    }

    /** Whether a non-zero effect is large enough to select; always true when no threshold applies. */
    private boolean isMaterial(double signedEffect) {
        return materialityThreshold.isEmpty()
                || Math.abs(signedEffect) >= materialityThreshold.getAsDouble() - MATERIALITY_TOLERANCE;
    }

    private List<FactorCandidate> exposureCandidates(ValidatedJudgments judgments) {
        return EXPOSURE_WEIGHTS.values().stream().map(weighted -> {
            ScoreValue score = judgments.score(weighted.dimensionId());
            double exposureComponent = weighted.value(score);
            double signedEffect = weighted.weight() * (exposureComponent - 0.5);
            return new FactorCandidate(weighted.dimensionId(), score.score(), exposureComponent,
                    weighted.weight(), signedEffect);
        }).toList();
    }

    private List<ProfessionalDecisionFactor> exposureFactors(List<FactorCandidate> candidates) {
        return candidates.stream().map(candidate -> {
            double signedEffect = candidate.signedEffect();
            if (signedEffect == 0.0) {
                return null;
            }
            return new ProfessionalDecisionFactor(
                    candidate.dimensionId(),
                    signedEffect > 0.0
                            ? DecisionFactorEffect.INCREASES_AI_EXPOSURE
                            : DecisionFactorEffect.REDUCES_AI_EXPOSURE,
                    ProfessionalFactorDirection.valueOf((candidate.rawScore() > 2.0 ? "HIGH_" : "LOW_")
                            + candidate.dimensionId().toUpperCase(Locale.ROOT)),
                    candidate.rawScore(),
                    candidate.transformedValue(),
                    candidate.weight(),
                    signedEffect);
        })
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, WeightedDimension> weightedDimensions(WeightedDimension... dimensions) {
        LinkedHashMap<String, WeightedDimension> weights = new LinkedHashMap<>();
        double total = 0.0;
        for (WeightedDimension dimension : dimensions) {
            if (weights.put(dimension.dimensionId(), dimension) != null) {
                throw new IllegalStateException("duplicate policy dimension: " + dimension.dimensionId());
            }
            total += dimension.weight();
        }
        if (Math.abs(total - 1.0) > 1.0e-9) {
            throw new IllegalStateException("policy weights must sum to 1");
        }
        return java.util.Collections.unmodifiableMap(weights);
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

    private InvalidProfessionalJudgmentSetException invalid(String message) {
        return new InvalidProfessionalJudgmentSetException(
                "Invalid professional judgment set: " + message);
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

    private record WeightedDimension(String dimensionId, double weight, boolean inverse) {

        double value(ScoreValue score) {
            DoubleUnaryOperator operation = inverse
                    ? ProfessionalScoreScale::inverse
                    : ProfessionalScoreScale::normalise;
            return operation.applyAsDouble(score.score());
        }

        String transformation() {
            return inverse ? "inverse: 1 - score / 4" : "normalise: score / 4";
        }
    }
}
