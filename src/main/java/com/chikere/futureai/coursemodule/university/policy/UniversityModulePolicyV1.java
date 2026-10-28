package com.chikere.futureai.coursemodule.university.policy;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic {@code university-module-policy-v1}: one primary category and safeguard flags per module, from the
 * {@code university-module-v1} judgments. Pure Java; it calls no provider and persists nothing.
 *
 * <p>Rules are evaluated in {@link UniversityModuleRule} order and the first that applies decides. A characteristic
 * dominates when it is HIGH (at least {@link #HIGH_FROM}) and leads every competing characteristic by at least
 * {@link #DOMINANCE_MARGIN}. The competing characteristics are practical application, human demand
 * ({@code max(human_judgement, interpersonal_work)}) and technical/quantitative depth.</p>
 *
 * <p>AI learning assistance never takes part in a dominance comparison and never classifies a module by itself:
 * in the Stage 5C live probe it was HIGH for every module (2.53 to 3.84), so it cannot tell modules apart. It
 * matters only once no characteristic dominates (UM4, UM5). {@code described_clearly} is diagnostic only and is
 * not read here.</p>
 *
 * <p>AI learning assistance describes where AI may support learning or preparation. It does not determine what use
 * is permitted in assessed work.</p>
 *
 * <p>Frozen in Stage 5D against the 56 live modules in {@code src/test/resources/university/module-probe-v1/}. Any
 * change to a rule, threshold or margin needs a new policy version.</p>
 */
public final class UniversityModulePolicyV1 {

    public static final String POLICY_VERSION = "university-module-policy-v1";
    public static final String SUPPORTED_SCHEMA_VERSION = "university-module-v1";

    /** 0-4 scale: LOW below 1.5, MODERATE from 1.5, HIGH from 2.5. */
    public static final double MODERATE_FROM = 1.5;
    public static final double HIGH_FROM = 2.5;
    public static final double DOMINANCE_MARGIN = 0.25;
    /** A deciding value this close to {@link #HIGH_FROM}, or a gap this close to the margin, is a close call. */
    public static final double CLOSE_CALL_BAND = 0.15;
    /**
     * AI assistance at least {@link #INTEGRITY_AI_FROM} and independent demonstration at least
     * {@link #INTEGRITY_INDEPENDENT_FROM}: AI may support learning or preparation, but demonstrating the student's own
     * capability is especially important. At 2.5 the independent threshold flagged 51 of the 56 live modules.
     */
    public static final double INTEGRITY_AI_FROM = 2.5;
    public static final double INTEGRITY_INDEPENDENT_FROM = 3.5;
    /** A strong {@code professional_standards} signal: the safeguard applies on its own. */
    public static final double PROFESSIONAL_STANDARDS_FROM = 0.75;
    /**
     * A moderate {@code professional_standards} signal: the safeguard applies only when the module is also practical
     * and interpersonal at HIGH, as in supervised practice with clients or patients.
     */
    public static final double PROFESSIONAL_STANDARDS_WITH_PRACTICE_FROM = 0.50;
    /** Floating-point allowance for every threshold comparison, far below any provider precision. */
    static final double TOLERANCE = 1.0e-9;

    public List<UniversityModuleClassification> classify(UniversityModuleJudgmentResult result) {
        Objects.requireNonNull(result, "result must not be null");
        if (!SUPPORTED_SCHEMA_VERSION.equals(result.schemaVersion())) {
            throw new IllegalArgumentException("module judgments must be " + SUPPORTED_SCHEMA_VERSION + ", were "
                    + result.schemaVersion());
        }
        return result.modules().stream().map(this::classify).toList();
    }

    public UniversityModuleClassification classify(UniversityModuleJudgmentSet module) {
        Objects.requireNonNull(module, "module must not be null");
        double ai = module.aiLearningAssistance().value();
        double independent = module.independentDemonstration().value();
        double practical = module.practicalApplication().value();
        double interpersonal = module.interpersonalWork().value();
        double human = Math.max(module.humanJudgement().value(), interpersonal);
        double technical = module.technicalQuantitative().value();
        List<String> close = new ArrayList<>();

        Decision decision;
        if (dominates(practical, Math.max(human, technical), "practical_application", close)) {
            decision = new Decision(UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL,
                    UniversityModuleRule.UM1_PRACTICAL_DOMINANCE, practical, Math.max(human, technical));
        }
        else if (handsOnWithPeople(practical, interpersonal, human, technical, close)) {
            decision = new Decision(UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL,
                    UniversityModuleRule.UM1B_HANDS_ON_WITH_PEOPLE, practical, Math.max(human, technical));
        }
        else if (dominates(human, Math.max(practical, technical), "human_demand", close)) {
            decision = new Decision(UniversityModuleCategory.HUMAN_JUDGEMENT_LED,
                    UniversityModuleRule.UM2_HUMAN_JUDGEMENT_DOMINANCE, human, Math.max(practical, technical));
        }
        else if (dominates(technical, Math.max(practical, human), "technical_quantitative", close)) {
            decision = new Decision(UniversityModuleCategory.TECHNICAL_CORE, UniversityModuleRule.UM3_TECHNICAL_CORE,
                    technical, Math.max(practical, human));
        }
        else if (high(ai) && high(independent)) {
            nearThreshold(independent, "independent_demonstration", close);
            decision = new Decision(UniversityModuleCategory.AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION,
                    UniversityModuleRule.UM4_AI_WITH_INDEPENDENT_DEMONSTRATION, ai, independent);
        }
        else if (high(ai) && !high(Math.max(practical, Math.max(human, technical)))) {
            nearThreshold(independent, "independent_demonstration", close);
            decision = new Decision(UniversityModuleCategory.AI_ASSISTED_LEARNING,
                    UniversityModuleRule.UM5_AI_ASSISTED_LEARNING, ai, independent);
        }
        else {
            decision = new Decision(UniversityModuleCategory.MIXED, UniversityModuleRule.UM6_MIXED, ai, independent);
        }

        Set<UniversityModuleFlag> flags = EnumSet.noneOf(UniversityModuleFlag.class);
        if (atLeast(ai, INTEGRITY_AI_FROM) && atLeast(independent, INTEGRITY_INDEPENDENT_FROM)) {
            flags.add(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE);
        }
        if (professionalStandardsRelevant(module.professionalStandards(), practical, interpersonal)) {
            flags.add(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT);
        }
        return new UniversityModuleClassification(module.moduleKey(), module.moduleTitle(), decision.category(),
                decision.rule(), inputs(module), decision.dominant(), decision.competing(),
                decision.dominant() - decision.competing(), flags, close);
    }

    /** A strong standards signal, or a moderate one reinforced by HIGH practical and interpersonal work. */
    private static boolean professionalStandardsRelevant(double standards, double practical, double interpersonal) {
        return atLeast(standards, PROFESSIONAL_STANDARDS_FROM)
                || atLeast(standards, PROFESSIONAL_STANDARDS_WITH_PRACTICE_FROM) && high(practical) && high(interpersonal);
    }

    /** HIGH and ahead of every competitor by the margin; records why the outcome was close either way. */
    private static boolean dominates(double value, double competing, String name, List<String> close) {
        nearThreshold(value, name, close);
        double gap = value - competing;
        if (high(value) && Math.abs(gap - DOMINANCE_MARGIN) < CLOSE_CALL_BAND - TOLERANCE) {
            close.add(name + " lead " + round(gap) + " near margin " + DOMINANCE_MARGIN);
        }
        return high(value) && atLeast(gap, DOMINANCE_MARGIN);
    }

    /**
     * Hands-on work with people, such as a placement, clinic or client brief: practical and interpersonal work are both
     * HIGH, practical is within the margin of the human demand (so UM1 did not apply), and practical leads technical
     * depth by the margin.
     */
    private static boolean handsOnWithPeople(double practical, double interpersonal, double human, double technical,
            List<String> close) {
        boolean applies = high(practical) && high(interpersonal) && atLeast(practical - technical, DOMINANCE_MARGIN)
                && Math.abs(practical - human) < DOMINANCE_MARGIN;
        if (applies) {
            nearThreshold(interpersonal, "interpersonal_work", close);
        }
        return applies;
    }

    private static void nearThreshold(double value, String name, List<String> close) {
        if (Math.abs(value - HIGH_FROM) < CLOSE_CALL_BAND - TOLERANCE) {
            close.add(name + " " + round(value) + " near HIGH " + HIGH_FROM);
        }
    }

    private static boolean high(double value) {
        return atLeast(value, HIGH_FROM);
    }

    private static boolean atLeast(double value, double threshold) {
        return value >= threshold - TOLERANCE;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static Map<String, Double> inputs(UniversityModuleJudgmentSet module) {
        Map<String, Double> inputs = new LinkedHashMap<>();
        inputs.put("described_clearly", module.describedClearly());
        inputs.put("professional_standards", module.professionalStandards());
        inputs.put("ai_learning_assistance", module.aiLearningAssistance().value());
        inputs.put("independent_demonstration", module.independentDemonstration().value());
        inputs.put("human_judgement", module.humanJudgement().value());
        inputs.put("interpersonal_work", module.interpersonalWork().value());
        inputs.put("practical_application", module.practicalApplication().value());
        inputs.put("technical_quantitative", module.technicalQuantitative().value());
        return inputs;
    }

    private record Decision(UniversityModuleCategory category, UniversityModuleRule rule, double dominant,
            double competing) {
    }
}
