package com.chikere.futureai.responsibility.professional.policy;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic responsibility classification, {@value #POLICY_VERSION}, calibrated on the Stage 4B live Jev probe
 * (Registered Nurse, Java Software Developer, Management Accountant; 24 responsibilities).
 *
 * <p><b>Levels.</b> A judgment is Low below {@link #MODERATE_FROM}, Moderate from it to {@link #HIGH_FROM}, High
 * from there: the rubric anchor it sits nearest. The same cut-offs apply to the regulated-context judgment.</p>
 *
 * <p><b>Category rules</b>, first match wins:</p>
 * <ol>
 *   <li>{@code R1_PHYSICAL_DOMINANT}: physical presence High and at least as high as human demand (the higher of
 *       contextual judgement and human interaction). The work is mainly physical or in person. When human demand
 *       is higher, as for supporting patients and families, the stronger signal is the human one, so the
 *       responsibility falls through to the next rule instead.</li>
 *   <li>{@code R2_HUMAN_DEMAND_DOMINATES}: human demand, the higher of contextual judgement and human interaction,
 *       is High and exceeds assistance scope by at least {@link #DOMINANCE_MARGIN}. Live assistance scope never
 *       fell below Moderate, so a rule based only on low assistance would never fire; the calibration showed that
 *       comparing the two is what separates mentoring or escalation from drafting work.</li>
 *   <li>{@code R3_ASSISTANCE_LOW}: assistance scope Low. Not seen in the live probe; kept for roles that produce it.</li>
 *   <li>{@code R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH}: assistance High while contextual judgement and human
 *       interaction are both below High.</li>
 *   <li>{@code R5_PARTIAL_ASSISTANCE}: everything else, meaning assistance High alongside high but not dominant
 *       human demand, or assistance Moderate.</li>
 * </ol>
 *
 * <p><b>Oversight marker</b>, independent of the category: {@code O1_ACCOUNTABILITY_VERY_HIGH} when outcome
 * accountability is at least {@link #VERY_HIGH_ACCOUNTABILITY_FROM} (nearest the top rubric anchor); otherwise
 * {@code O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY} when the role-level regulated context is at least Moderate,
 * accountability is High and assistance scope is at least Moderate. A Moderate regulated context is a safeguarding
 * signal for individual responsibilities, never a label for the whole profession.</p>
 *
 * <p>The provider's clarity answer is recorded but never used: in the calibration it was low for responsibilities
 * that were clearly understood and judged coherently. No role-level score, band or factor is read or changed.</p>
 */
public final class ProfessionalResponsibilityPolicyV1 implements ProfessionalResponsibilityClassifier {

    public static final String POLICY_VERSION = "professional-responsibility-policy-v1";
    public static final String SUPPORTED_SCHEMA_VERSION = "professional-responsibility-v1";

    public static final double MODERATE_FROM = 1.5;
    public static final double HIGH_FROM = 2.5;
    public static final double DOMINANCE_MARGIN = 0.25;
    public static final double VERY_HIGH_ACCOUNTABILITY_FROM = 3.5;
    /**
     * A deciding comparison closer than this to its threshold marks the outcome a close call. In the live probe
     * the same judgment moved by a median of 0.04 and a 75th percentile of 0.07 between calls, so 0.15 is about
     * twice the typical movement.
     */
    public static final double CLOSE_CALL_BAND = 0.15;

    private final AssessmentTrace trace;

    public ProfessionalResponsibilityPolicyV1() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the outcome; it never changes it. */
    public ProfessionalResponsibilityPolicyV1(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    @Override
    public ProfessionalResponsibilityClassificationResult classify(ProfessionalResponsibilityJudgmentResult judgments) {
        Objects.requireNonNull(judgments, "judgments must not be null");
        if (!SUPPORTED_SCHEMA_VERSION.equals(judgments.schemaVersion())) {
            throw new IllegalArgumentException(POLICY_VERSION + " classifies only " + SUPPORTED_SCHEMA_VERSION
                    + " judgments, not " + judgments.schemaVersion());
        }
        double regulated = judgments.regulatedContext().value();
        List<ResponsibilityClassification> classifications = new ArrayList<>();
        for (ResponsibilityJudgmentSet set : judgments.responsibilities()) {
            classifications.add(classify(set, regulated));
        }
        var result = new ProfessionalResponsibilityClassificationResult(POLICY_VERSION, judgments.schemaVersion(),
                level(regulated), regulated, classifications);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_CLASSIFICATION, () -> tracePayload(result));
        return result;
    }

    /** The explanatory level of a 0-4 value. */
    public static ResponsibilityLevel level(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 4) {
            throw new IllegalArgumentException("a judgment value must be finite and between 0 and 4");
        }
        if (value < MODERATE_FROM) return ResponsibilityLevel.LOW;
        if (value < HIGH_FROM) return ResponsibilityLevel.MODERATE;
        return ResponsibilityLevel.HIGH;
    }

    private ResponsibilityClassification classify(ResponsibilityJudgmentSet set, double regulated) {
        Map<ResponsibilityJudgmentKind, Double> values = new EnumMap<>(ResponsibilityJudgmentKind.class);
        Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels = new EnumMap<>(ResponsibilityJudgmentKind.class);
        for (ResponsibilityJudgmentKind kind : ResponsibilityJudgmentKind.values()) {
            values.put(kind, kind.value(set));
            levels.put(kind, level(kind.value(set)));
        }
        double assistance = values.get(ResponsibilityJudgmentKind.ASSISTANCE_SCOPE);
        double context = values.get(ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT);
        double interaction = values.get(ResponsibilityJudgmentKind.HUMAN_INTERACTION);
        double physical = values.get(ResponsibilityJudgmentKind.PHYSICAL_PRESENCE);
        double accountability = values.get(ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY);
        double humanDemand = Math.max(context, interaction);

        // Every comparison evaluated on the way to the matching rule; any within the band makes it a close call.
        Comparisons deciding = new Comparisons();
        ResponsibilityCategory category;
        String rule;
        if (deciding.atLeast(physical, HIGH_FROM) && deciding.atLeast(physical - humanDemand, 0)) {
            category = ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON;
            rule = "R1_PHYSICAL_DOMINANT";
        }
        // The margin is compared only once human demand is High; a margin under a lower demand decides nothing.
        else if (deciding.atLeast(humanDemand, HIGH_FROM) && deciding.atLeast(humanDemand - assistance, DOMINANCE_MARGIN)) {
            category = ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING;
            rule = "R2_HUMAN_DEMAND_DOMINATES";
        }
        else if (!deciding.atLeast(assistance, MODERATE_FROM)) {
            category = ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING;
            rule = "R3_ASSISTANCE_LOW";
        }
        else if (deciding.atLeast(assistance, HIGH_FROM) & !deciding.atLeast(context, HIGH_FROM)
                & !deciding.atLeast(interaction, HIGH_FROM)) {
            category = ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY;
            rule = "R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH";
        }
        else {
            category = ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT;
            rule = "R5_PARTIAL_ASSISTANCE";
        }

        Optional<String> oversight;
        if (accountability >= VERY_HIGH_ACCOUNTABILITY_FROM) {
            oversight = Optional.of("O1_ACCOUNTABILITY_VERY_HIGH");
        }
        else if (regulated >= MODERATE_FROM && accountability >= HIGH_FROM && assistance >= MODERATE_FROM) {
            oversight = Optional.of("O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY");
        }
        else {
            oversight = Optional.empty();
        }
        return new ResponsibilityClassification(set.responsibilityIndex(), set.responsibilityKey(), category,
                oversight.isPresent(), levels, values, rule, oversight, deciding.close, set.describedClearly());
    }

    private Map<String, Object> tracePayload(ProfessionalResponsibilityClassificationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", result.schemaVersion());
        payload.put("policyVersion", result.policyVersion());
        payload.put("regulatedContext", Map.of("value", result.regulatedContextValue(),
                "level", result.regulatedContextLevel()));
        payload.put("thresholds", Map.of("moderateFrom", MODERATE_FROM, "highFrom", HIGH_FROM,
                "dominanceMargin", DOMINANCE_MARGIN, "veryHighAccountabilityFrom", VERY_HIGH_ACCOUNTABILITY_FROM,
                "closeCallBand", CLOSE_CALL_BAND));
        List<Map<String, Object>> items = new ArrayList<>();
        for (ResponsibilityClassification classification : result.classifications()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("responsibilityKey", classification.responsibilityKey());
            item.put("values", classification.values());
            item.put("levels", classification.levels());
            item.put("ruleId", classification.ruleId());
            item.put("category", classification.category());
            item.put("needsCloseHumanOversight", classification.needsCloseHumanOversight());
            item.put("oversightRule", classification.oversightRule().orElse(null));
            item.put("closeCall", classification.closeCall());
            item.put("describedClearly", Map.of("value", classification.describedClearly(),
                    "use", "diagnostic only; not used by " + POLICY_VERSION));
            items.add(item);
        }
        payload.put("classifications", items);
        return payload;
    }

    /** Records whether any comparison made was within the close-call band of its threshold. */
    private static final class Comparisons {
        private boolean close;

        boolean atLeast(double value, double threshold) {
            if (Math.abs(value - threshold) < CLOSE_CALL_BAND) {
                close = true;
            }
            return value >= threshold;
        }
    }
}
