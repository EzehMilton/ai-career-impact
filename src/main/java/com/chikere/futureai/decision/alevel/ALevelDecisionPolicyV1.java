package com.chikere.futureai.decision.alevel;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;

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
 * Deterministic product policy for {@code a-level-v1} judgments.
 *
 * <p>These weights and thresholds are transparent, <strong>provisional V1 product policy</strong>.
 * They are not educational science, they are not validated against any outcome data, and they do
 * not represent any admissions body's view. Changing any of them requires a new policy version so
 * an existing version never silently changes meaning.</p>
 *
 * <p>Future Optionality is not professional AI Exposure inverted and not university Future
 * Readiness renamed. It asks only how wide a range of plausible next steps a subject combination
 * appears to keep open, and how transferable the capabilities it develops are.</p>
 *
 * <p>This class is pure Java. It calls no judgment provider, no model, no persistence and no
 * evidence source. It also contains no table of subjects: nothing here knows that Mathematics
 * exists. It combines judgments about the <em>combination</em>, never points awarded per subject.</p>
 */
public final class ALevelDecisionPolicyV1 {

    public static final String POLICY_VERSION = "a-level-decision-v1";

    private static final String SCHEMA_VERSION = "a-level-v1";
    private static final String INFORMATION_SUFFICIENT = "information_sufficient";
    private static final String QUANTITATIVE_REASONING = "quantitative_reasoning";
    private static final String ANALYTICAL_REASONING = "analytical_reasoning";
    private static final String WRITTEN_VERBAL_COMMUNICATION = "written_verbal_communication";
    private static final String ORIGINAL_JUDGMENT_CREATIVITY = "original_judgment_creativity";
    private static final String CONTEXTUAL_SOCIAL_UNDERSTANDING = "contextual_social_understanding";
    private static final String TECHNICAL_DIGITAL_FOUNDATION = "technical_digital_foundation";
    private static final String SUBJECT_SKILL_DIVERSITY = "subject_skill_diversity";
    private static final String DEGREE_PATHWAY_BREADTH = "degree_pathway_breadth";
    private static final String SKILL_TRANSFERABILITY = "skill_transferability";
    private static final String AI_ERA_COMPLEMENTARITY = "ai_era_complementarity";

    /** The ten Score dimensions, in schema order. */
    public static final List<String> SCORE_DIMENSIONS = List.of(
            QUANTITATIVE_REASONING,
            ANALYTICAL_REASONING,
            WRITTEN_VERBAL_COMMUNICATION,
            ORIGINAL_JUDGMENT_CREATIVITY,
            CONTEXTUAL_SOCIAL_UNDERSTANDING,
            TECHNICAL_DIGITAL_FOUNDATION,
            SUBJECT_SKILL_DIVERSITY,
            DEGREE_PATHWAY_BREADTH,
            SKILL_TRANSFERABILITY,
            AI_ERA_COMPLEMENTARITY);

    private static final double INFORMATION_REQUIRED_THRESHOLD = 0.60;
    private static final double READY_INFORMATION_THRESHOLD = 0.75;
    private static final double HIGH_INFORMATION_THRESHOLD = 0.80;
    private static final double MODERATE_MEAN_CONFIDENCE_THRESHOLD = 0.65;
    private static final double HIGH_MEAN_CONFIDENCE_THRESHOLD = 0.80;
    private static final double HIGH_MINIMUM_CONFIDENCE_THRESHOLD = 0.60;
    private static final double BALANCED_BAND_THRESHOLD = 3.50;
    private static final double BROAD_BAND_THRESHOLD = 7.00;

    /**
     * Future Optionality weights.
     *
     * <p>Breadth of plausible pathways and transferability dominate because they are the most
     * direct expressions of optionality. Analytical and quantitative reasoning follow because they
     * act as gateway capabilities for a wide span of degrees. Skill diversity contributes because
     * variety itself preserves options. The remaining capability types each contribute modestly;
     * they are carried in full by their own supporting measures, so a combination strong in
     * communication or creativity is not under-reported overall.</p>
     */
    private static final Map<String, WeightedDimension> FUTURE_OPTIONALITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(DEGREE_PATHWAY_BREADTH, 0.250),
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.200),
            new WeightedDimension(ANALYTICAL_REASONING, 0.150),
            new WeightedDimension(QUANTITATIVE_REASONING, 0.100),
            new WeightedDimension(SUBJECT_SKILL_DIVERSITY, 0.100),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.050),
            new WeightedDimension(WRITTEN_VERBAL_COMMUNICATION, 0.050),
            new WeightedDimension(CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.050),
            new WeightedDimension(TECHNICAL_DIGITAL_FOUNDATION, 0.025),
            new WeightedDimension(AI_ERA_COMPLEMENTARITY, 0.025));

    private static final Map<String, WeightedDimension> DEGREE_PATHWAY_BREADTH_WEIGHTS = weightedDimensions(
            new WeightedDimension(DEGREE_PATHWAY_BREADTH, 0.75),
            new WeightedDimension(SUBJECT_SKILL_DIVERSITY, 0.25));

    private static final Map<String, WeightedDimension> ANALYTICAL_FOUNDATION_WEIGHTS = weightedDimensions(
            new WeightedDimension(ANALYTICAL_REASONING, 0.40),
            new WeightedDimension(QUANTITATIVE_REASONING, 0.40),
            new WeightedDimension(TECHNICAL_DIGITAL_FOUNDATION, 0.20));

    private static final Map<String, WeightedDimension> HUMAN_AND_CREATIVE_WEIGHTS = weightedDimensions(
            new WeightedDimension(WRITTEN_VERBAL_COMMUNICATION, 0.35),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.35),
            new WeightedDimension(CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.30));

    private static final Map<String, WeightedDimension> SKILL_TRANSFERABILITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.45),
            new WeightedDimension(SUBJECT_SKILL_DIVERSITY, 0.25),
            new WeightedDimension(ANALYTICAL_REASONING, 0.15),
            new WeightedDimension(WRITTEN_VERBAL_COMMUNICATION, 0.15));

    private static final Map<String, WeightedDimension> AI_ERA_ADAPTABILITY_WEIGHTS = weightedDimensions(
            new WeightedDimension(AI_ERA_COMPLEMENTARITY, 0.35),
            new WeightedDimension(ANALYTICAL_REASONING, 0.20),
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.20),
            new WeightedDimension(TECHNICAL_DIGITAL_FOUNDATION, 0.15),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.10));

    /**
     * Explanation-only weights covering all ten Score dimensions.
     *
     * <p>They select and order strengths and development opportunities. They are deliberately
     * flatter than the Future Optionality weights so that a capability carried mainly by a
     * supporting measure can still be named as a strength. They never contribute to an
     * authoritative measure.</p>
     */
    private static final Map<String, WeightedDimension> EXPLANATION_WEIGHTS = weightedDimensions(
            new WeightedDimension(DEGREE_PATHWAY_BREADTH, 0.15),
            new WeightedDimension(SKILL_TRANSFERABILITY, 0.14),
            new WeightedDimension(ANALYTICAL_REASONING, 0.12),
            new WeightedDimension(QUANTITATIVE_REASONING, 0.10),
            new WeightedDimension(SUBJECT_SKILL_DIVERSITY, 0.10),
            new WeightedDimension(ORIGINAL_JUDGMENT_CREATIVITY, 0.09),
            new WeightedDimension(WRITTEN_VERBAL_COMMUNICATION, 0.09),
            new WeightedDimension(CONTEXTUAL_SOCIAL_UNDERSTANDING, 0.08),
            new WeightedDimension(TECHNICAL_DIGITAL_FOUNDATION, 0.07),
            new WeightedDimension(AI_ERA_COMPLEMENTARITY, 0.06));

    public ALevelDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        ValidatedJudgments judgments = validate(judgmentSet);
        double informationSufficiency = judgments.informationSufficiency();

        if (informationSufficiency < INFORMATION_REQUIRED_THRESHOLD) {
            AssessmentDecision headline = new AssessmentDecision(
                    JourneyType.A_LEVEL,
                    OptionalDouble.empty(),
                    "MORE INFORMATION REQUIRED",
                    AssessmentConfidence.LOW,
                    AssessmentStatus.MORE_INFORMATION_REQUIRED,
                    List.of(),
                    List.of());
            return new ALevelDecisionResult(
                    POLICY_VERSION, informationSufficiency, headline, Map.of(), List.of(), List.of());
        }

        AssessmentConfidence assessmentConfidence = assessmentConfidence(judgments);
        AssessmentStatus status = assessmentStatus(informationSufficiency, assessmentConfidence);
        EnumMap<ALevelMeasure, ALevelMeasureResult> measures = calculateMeasures(judgments);
        ALevelMeasureResult optionality = measures.get(ALevelMeasure.FUTURE_OPTIONALITY);

        List<ALevelDecisionFactor> factors = explanationFactors(judgments);
        List<ALevelDecisionFactor> strengths = factors.stream()
                .filter(factor -> factor.signedEffect() > 0.0)
                .sorted(Comparator.comparingDouble(ALevelDecisionFactor::signedEffect).reversed())
                .limit(3)
                .toList();
        List<ALevelDecisionFactor> opportunities = factors.stream()
                .filter(factor -> factor.signedEffect() < 0.0)
                .sorted(Comparator.comparingDouble(ALevelDecisionFactor::signedEffect))
                .limit(3)
                .toList();

        AssessmentDecision headline = new AssessmentDecision(
                JourneyType.A_LEVEL,
                OptionalDouble.of(optionality.indicator()),
                optionality.band().name() + " FUTURE OPTIONALITY",
                assessmentConfidence,
                status,
                strengths.stream().map(ALevelDecisionFactor::dimensionId).toList(),
                opportunities.stream().map(ALevelDecisionFactor::dimensionId).toList());
        return new ALevelDecisionResult(
                POLICY_VERSION, informationSufficiency, headline, measures, strengths, opportunities);
    }

    public static OptionalityBand optionalityBand(double indicator) {
        if (!Double.isFinite(indicator) || indicator < 0.0 || indicator > 10.0) {
            throw new IllegalArgumentException("indicator must be finite and between 0 and 10");
        }
        if (indicator < BALANCED_BAND_THRESHOLD) {
            return OptionalityBand.FOCUSED;
        }
        if (indicator < BROAD_BAND_THRESHOLD) {
            return OptionalityBand.BALANCED;
        }
        return OptionalityBand.BROAD;
    }

    /** The explicit, testable V1 weights for one measure. */
    public static Map<String, Double> weightsFor(ALevelMeasure measure) {
        return asWeights(weightsOf(measure));
    }

    /** The explicit, testable V1 explanation weights. They never affect an authoritative measure. */
    public static Map<String, Double> explanationWeights() {
        return asWeights(EXPLANATION_WEIGHTS);
    }

    private static Map<String, Double> asWeights(Map<String, WeightedDimension> weights) {
        LinkedHashMap<String, Double> copy = new LinkedHashMap<>();
        weights.forEach((dimension, weighted) -> copy.put(dimension, weighted.weight()));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, WeightedDimension> weightsOf(ALevelMeasure measure) {
        return switch (measure) {
            case FUTURE_OPTIONALITY -> FUTURE_OPTIONALITY_WEIGHTS;
            case DEGREE_PATHWAY_BREADTH -> DEGREE_PATHWAY_BREADTH_WEIGHTS;
            case ANALYTICAL_FOUNDATION -> ANALYTICAL_FOUNDATION_WEIGHTS;
            case HUMAN_AND_CREATIVE_STRENGTH -> HUMAN_AND_CREATIVE_WEIGHTS;
            case SKILL_TRANSFERABILITY -> SKILL_TRANSFERABILITY_WEIGHTS;
            case AI_ERA_ADAPTABILITY -> AI_ERA_ADAPTABILITY_WEIGHTS;
        };
    }

    private ValidatedJudgments validate(AssessmentJudgmentSet judgmentSet) {
        Objects.requireNonNull(judgmentSet, "judgmentSet must not be null");
        if (judgmentSet.journey() != JourneyType.A_LEVEL) {
            throw invalid("journey must be A_LEVEL");
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

    private EnumMap<ALevelMeasure, ALevelMeasureResult> calculateMeasures(ValidatedJudgments judgments) {
        EnumMap<ALevelMeasure, ALevelMeasureResult> results = new EnumMap<>(ALevelMeasure.class);
        for (ALevelMeasure measure : ALevelMeasure.values()) {
            results.put(measure, compositeMeasure(measure, weightsOf(measure), judgments));
        }
        return results;
    }

    private ALevelMeasureResult compositeMeasure(ALevelMeasure measure,
            Map<String, WeightedDimension> weights, ValidatedJudgments judgments) {
        double composite = weights.values().stream()
                .mapToDouble(weighted -> weighted.weight()
                        * ALevelScoreScale.normalise(judgments.score(weighted.dimensionId()).score()))
                .sum();
        double confidence = weights.values().stream()
                .mapToDouble(weighted -> weighted.weight() * judgments.score(weighted.dimensionId()).confidence())
                .sum();
        double indicator = ALevelScoreScale.toIndicator(composite);
        return new ALevelMeasureResult(measure, indicator, optionalityBand(indicator), confidence);
    }

    private AssessmentConfidence assessmentConfidence(ValidatedJudgments judgments) {
        double mean = SCORE_DIMENSIONS.stream()
                .map(judgments::score).mapToDouble(ScoreValue::confidence).average().orElseThrow();
        double minimum = SCORE_DIMENSIONS.stream()
                .map(judgments::score).mapToDouble(ScoreValue::confidence).min().orElseThrow();

        if (judgments.informationSufficiency() >= HIGH_INFORMATION_THRESHOLD
                && mean >= HIGH_MEAN_CONFIDENCE_THRESHOLD
                && minimum >= HIGH_MINIMUM_CONFIDENCE_THRESHOLD) {
            return AssessmentConfidence.HIGH;
        }
        if (judgments.informationSufficiency() >= INFORMATION_REQUIRED_THRESHOLD
                && mean >= MODERATE_MEAN_CONFIDENCE_THRESHOLD) {
            return AssessmentConfidence.MODERATE;
        }
        return AssessmentConfidence.LOW;
    }

    private AssessmentStatus assessmentStatus(double informationSufficiency, AssessmentConfidence confidence) {
        if (informationSufficiency < READY_INFORMATION_THRESHOLD || confidence != AssessmentConfidence.HIGH) {
            return AssessmentStatus.READY_WITH_CAUTION;
        }
        return AssessmentStatus.READY;
    }

    private List<ALevelDecisionFactor> explanationFactors(ValidatedJudgments judgments) {
        return EXPLANATION_WEIGHTS.values().stream().map(weighted -> {
            ScoreValue score = judgments.score(weighted.dimensionId());
            double optionalityComponent = ALevelScoreScale.normalise(score.score());
            double signedEffect = weighted.weight() * (optionalityComponent - 0.5);
            if (signedEffect == 0.0) {
                return null;
            }
            return new ALevelDecisionFactor(
                    weighted.dimensionId(),
                    signedEffect > 0.0
                            ? ALevelFactorEffect.STRENGTH
                            : ALevelFactorEffect.DEVELOPMENT_OPPORTUNITY,
                    ALevelFactorDirection.valueOf((score.score() > 2.0 ? "HIGH_" : "LOW_")
                            + weighted.dimensionId().toUpperCase(Locale.ROOT)),
                    score.score(), optionalityComponent, weighted.weight(), signedEffect);
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
            throw new IllegalStateException("policy weights must sum to 1 but summed to " + total);
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

    private InvalidALevelJudgmentSetException invalid(String message) {
        return new InvalidALevelJudgmentSetException("Invalid A-level judgment set: " + message);
    }

    private record ScoreValue(double score, double confidence) {
    }

    private record ValidatedJudgments(double informationSufficiency, Map<String, ScoreValue> scores) {
        ScoreValue score(String dimensionId) {
            return scores.get(dimensionId);
        }
    }

    private record WeightedDimension(String dimensionId, double weight) {
    }
}
