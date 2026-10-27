package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.HumanValueReason;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.OpportunityType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.PlanPhase;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardImportance;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.NinetyDayPlan;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.ResponsibleUseSafeguard;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic paid guidance, {@value #POLICY_VERSION}, derived only from a stored responsibility analysis: its
 * judgments, classifications, oversight markers, close calls and regulated context. No profession, provider or
 * model is consulted, and nothing here changes the analysis or the role-level assessment. Calibrated on the Stage 4E
 * seven-role live probe.
 *
 * <p><b>AI-assistance opportunities</b> (up to {@value #MAXIMUM_OPPORTUNITIES}). Candidates are responsibilities
 * classified "Good opportunity" or "AI may assist with parts" with assistance scope at least High. Each is scored:</p>
 * <pre>
 * score = assistance scope
 *       + category bonus (Good opportunity 1.0, parts 0.5, human-led 0)
 *       - 0.3 x each of contextual judgement, human interaction and physical presence above 2.0
 *       - 0.25 when the classification was a close call
 *       - governance penalty:
 *           0.4 for very high accountability oversight (O1, {@value #GOVERNANCE_O1})
 *           0.2 for regulated-context oversight (O2, {@value #GOVERNANCE_O2})
 *         + 0.3 x accountability above 2.5 when the regulated context is at least Moderate
 *           ({@value #GOVERNANCE_REGULATED_ACCOUNTABILITY})
 *         + 0.3 for O1 work with physical presence of at least 2.0 ({@value #GOVERNANCE_SAFETY_CRITICAL})
 * </pre>
 * <p>and ranked by score, then by responsibility order. The governance penalty never makes a responsibility
 * ineligible and never removes a safeguard: it only lets a lower-governance opportunity rank ahead of sensitive,
 * regulated or safety-critical work when both are eligible. It uses only the stored oversight rule, accountability,
 * physical presence and regulated context. Only when fewer than two candidates exist may one human-led
 * responsibility with High assistance be added, last, as preparation-only support. Hands-on work is never listed.</p>
 *
 * <p><b>Human-value areas</b> (up to {@value #MAXIMUM_HUMAN_VALUE_AREAS}). Candidates are human-led and hands-on
 * responsibilities, and "parts" responsibilities whose human demand is at least {@value #STRONG_HUMAN_DEMAND}. The
 * reason is the highest of interaction, contextual judgement and (when hands-on or High) physical presence; if none
 * is High, accountability. Score = that value + 1.0 for human-led or hands-on + 0.25 with oversight - 0.25 for a
 * close call. The best responsibility for each distinct reason is chosen first, then the rest by score.</p>
 *
 * <p><b>Skills, safeguards and the 90-day plan</b> are selected by the explicit triggers in this class, consolidated
 * so each family or safeguard appears once with every responsibility that supports it. The plan's pilot starts with
 * the highest-ranked opportunity that needs the least oversight (none, then regulated-context oversight, then very
 * high accountability), so the first trial is the lowest-risk one rather than simply the highest-scoring.</p>
 */
public final class ProfessionalGuidancePolicyV1 implements ProfessionalResponsibilityGuidancePolicy {

    public static final String POLICY_VERSION = "professional-guidance-policy-v1";

    public static final int MAXIMUM_OPPORTUNITIES = 3;
    public static final int MAXIMUM_HUMAN_VALUE_AREAS = 3;
    public static final int MAXIMUM_SKILL_PRIORITIES = 4;
    public static final int MAXIMUM_SAFEGUARDS = 4;
    public static final int MAXIMUM_ACTIONS_PER_PHASE = 4;

    static final double HIGH = ProfessionalResponsibilityPolicyV1.HIGH_FROM;
    static final double MODERATE = ProfessionalResponsibilityPolicyV1.MODERATE_FROM;
    static final double VERY_HIGH_ACCOUNTABILITY = ProfessionalResponsibilityPolicyV1.VERY_HIGH_ACCOUNTABILITY_FROM;
    static final double PENALTY_FROM = 2.0;
    static final double PENALTY_WEIGHT = 0.3;
    static final double CLOSE_CALL_PENALTY = 0.25;
    static final double O1_PENALTY = 0.4;
    static final double O2_PENALTY = 0.2;
    static final double REGULATED_ACCOUNTABILITY_WEIGHT = 0.3;
    static final double SAFETY_CRITICAL_PENALTY = 0.3;
    static final double SAFETY_CRITICAL_PHYSICAL_FROM = 2.0;
    static final String GOVERNANCE_O1 = "GV1_O1_VERY_HIGH_ACCOUNTABILITY";
    static final String GOVERNANCE_O2 = "GV2_O2_REGULATED_OVERSIGHT";
    static final String GOVERNANCE_REGULATED_ACCOUNTABILITY = "GV3_REGULATED_HIGH_ACCOUNTABILITY";
    static final String GOVERNANCE_SAFETY_CRITICAL = "GV4_SAFETY_CRITICAL_PHYSICAL_WORK";
    private static final String O1_RULE = "O1_ACCOUNTABILITY_VERY_HIGH";
    static final double STRONG_HUMAN_DEMAND = 3.0;

    @Override
    public ProfessionalResponsibilityGuidance decide(ProfessionalResponsibilityAnalysisSnapshot analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");
        double regulated = analysis.regulatedContext().value();
        List<ResponsibilityAnalysisItem> items = analysis.items();
        List<AiAssistanceOpportunity> opportunities = opportunities(items, regulated);
        List<HumanValueArea> humanValue = humanValueAreas(items);
        List<SkillPriority> skills = skills(items, opportunities, regulated);
        List<ResponsibleUseSafeguard> safeguards = safeguards(items, opportunities, regulated);
        NinetyDayPlan plan = plan(items, opportunities, humanValue, skills, safeguards, regulated);
        return new ProfessionalResponsibilityGuidance(POLICY_VERSION, analysis.snapshotId(), opportunities, humanValue,
                skills, plan, safeguards);
    }

    // ---- Opportunities ----

    private List<AiAssistanceOpportunity> opportunities(List<ResponsibilityAnalysisItem> items, double regulated) {
        List<ResponsibilityAnalysisItem> primary = items.stream()
                .filter(item -> (item.category() == ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY
                        || item.category() == ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT)
                        && value(item, ResponsibilityJudgmentKind.ASSISTANCE_SCOPE) >= HIGH)
                .sorted(byOpportunityScore(regulated)).limit(MAXIMUM_OPPORTUNITIES).toList();
        List<ResponsibilityAnalysisItem> chosen = new ArrayList<>(primary);
        if (primary.size() < 2) {
            items.stream().filter(item -> item.category() == ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING
                            && value(item, ResponsibilityJudgmentKind.ASSISTANCE_SCOPE) >= HIGH)
                    .sorted(byOpportunityScore(regulated)).limit(1).forEach(chosen::add);
        }
        List<AiAssistanceOpportunity> result = new ArrayList<>();
        for (ResponsibilityAnalysisItem item : chosen) {
            Map<String, Double> components = opportunityComponents(item, regulated);
            List<String> governance = governanceRules(item, regulated);
            boolean supporting = item.category() == ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING;
            OpportunityType type = supporting ? OpportunityType.PREPARATION_ONLY_FOR_HUMAN_LED_WORK
                    : item.category() == ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY
                            ? OpportunityType.PREPARATION_AND_INFORMATION_SUPPORT
                            : OpportunityType.SUPPORT_FOR_PARTS_UNDER_YOUR_JUDGEMENT;
            List<SafeUseConstraint> constraints = new ArrayList<>(List.of(SafeUseConstraint.REVIEW_EVERY_OUTPUT));
            if (regulated >= MODERATE || item.needsCloseHumanOversight()) {
                constraints.add(SafeUseConstraint.APPROVED_TOOLS_ONLY);
            }
            if (item.category() != ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY
                    || value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT) >= HIGH) {
                constraints.add(SafeUseConstraint.KEEP_DECISIONS_HUMAN);
            }
            result.add(new AiAssistanceOpportunity(result.size() + 1, item.responsibilityIndex(), item.responsibilityKey(),
                    item.responsibilityText(), item.category(), item.needsCloseHumanOversight(), type, type.guidance(),
                    constraints, components.get("total"), components,
                    supporting ? "OP2_SUPPORTING_HUMAN_LED_PREPARATION" : "OP1_PRIMARY_ASSISTANCE", item.closeCall(),
                    "Assistance scope " + level(item, ResponsibilityJudgmentKind.ASSISTANCE_SCOPE) + "; " + item.displayLabel()
                            + "; contextual judgement " + level(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT)
                            + ", human interaction " + level(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION)
                            + ", physical presence " + level(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE) + "."
                            + (governance.isEmpty() ? "" : " Ranked lower for governance (" + String.join(", ", governance)
                                    + "); still eligible with the safeguards below.")));
        }
        return result;
    }

    private Comparator<ResponsibilityAnalysisItem> byOpportunityScore(double regulated) {
        return Comparator.<ResponsibilityAnalysisItem>comparingDouble(item -> -opportunityComponents(item, regulated).get("total"))
                .thenComparingInt(ResponsibilityAnalysisItem::responsibilityIndex);
    }

    static Map<String, Double> opportunityComponents(ResponsibilityAnalysisItem item, double regulated) {
        Map<String, Double> components = new LinkedHashMap<>();
        components.put("assistance", value(item, ResponsibilityJudgmentKind.ASSISTANCE_SCOPE));
        components.put("categoryBonus", switch (item.category()) {
            case GOOD_AI_ASSISTANCE_OPPORTUNITY -> 1.0;
            case AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT -> 0.5;
            default -> 0.0;
        });
        components.put("contextPenalty", -penalty(value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT)));
        components.put("interactionPenalty", -penalty(value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION)));
        components.put("physicalPenalty", -penalty(value(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE)));
        components.put("closeCallPenalty", item.closeCall() ? -CLOSE_CALL_PENALTY : 0.0);
        boolean o1 = item.oversightRule().filter(O1_RULE::equals).isPresent();
        components.put("governanceOversightPenalty", o1 ? -O1_PENALTY : item.needsCloseHumanOversight() ? -O2_PENALTY : 0.0);
        components.put("governanceAccountabilityPenalty", regulated >= MODERATE
                ? -REGULATED_ACCOUNTABILITY_WEIGHT * Math.max(0, value(item, ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY) - HIGH)
                : 0.0);
        components.put("governanceSafetyCriticalPenalty", o1 && value(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE)
                >= SAFETY_CRITICAL_PHYSICAL_FROM ? -SAFETY_CRITICAL_PENALTY : 0.0);
        components.put("total", components.values().stream().mapToDouble(Double::doubleValue).sum());
        return components;
    }

    /** The stable IDs of the governance components that lowered this responsibility's rank. */
    static List<String> governanceRules(ResponsibilityAnalysisItem item, double regulated) {
        Map<String, Double> components = opportunityComponents(item, regulated);
        List<String> rules = new ArrayList<>();
        double oversight = components.get("governanceOversightPenalty");
        if (oversight != 0.0) {
            rules.add(oversight == -O1_PENALTY ? GOVERNANCE_O1 : GOVERNANCE_O2);
        }
        if (components.get("governanceAccountabilityPenalty") != 0.0) {
            rules.add(GOVERNANCE_REGULATED_ACCOUNTABILITY);
        }
        if (components.get("governanceSafetyCriticalPenalty") != 0.0) {
            rules.add(GOVERNANCE_SAFETY_CRITICAL);
        }
        return rules;
    }

    private static double penalty(double value) {
        return PENALTY_WEIGHT * Math.max(0, value - PENALTY_FROM);
    }

    // ---- Human-value areas ----

    private List<HumanValueArea> humanValueAreas(List<ResponsibilityAnalysisItem> items) {
        List<ResponsibilityAnalysisItem> candidates = items.stream().filter(item ->
                        item.category() == ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING
                                || item.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON
                                || (item.category() == ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT
                                        && humanDemand(item) >= STRONG_HUMAN_DEMAND))
                .sorted(Comparator.<ResponsibilityAnalysisItem>comparingDouble(item -> -humanValueScore(item))
                        .thenComparingInt(ResponsibilityAnalysisItem::responsibilityIndex))
                .toList();
        List<ResponsibilityAnalysisItem> picked = new ArrayList<>();
        Set<HumanValueReason> reasons = new LinkedHashSet<>();
        for (ResponsibilityAnalysisItem item : candidates) {
            if (picked.size() < MAXIMUM_HUMAN_VALUE_AREAS && reasons.add(reason(item))) picked.add(item);
        }
        for (ResponsibilityAnalysisItem item : candidates) {
            if (picked.size() < MAXIMUM_HUMAN_VALUE_AREAS && !picked.contains(item)) picked.add(item);
        }
        picked.sort(Comparator.<ResponsibilityAnalysisItem>comparingDouble(item -> -humanValueScore(item))
                .thenComparingInt(ResponsibilityAnalysisItem::responsibilityIndex));
        List<HumanValueArea> result = new ArrayList<>();
        for (ResponsibilityAnalysisItem item : picked) {
            HumanValueReason reason = reason(item);
            List<HumanValueReason> supporting = new ArrayList<>();
            if (reason != HumanValueReason.HUMAN_INTERACTION && value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION) >= HIGH)
                supporting.add(HumanValueReason.HUMAN_INTERACTION);
            if (reason != HumanValueReason.CONTEXTUAL_JUDGEMENT && value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT) >= HIGH)
                supporting.add(HumanValueReason.CONTEXTUAL_JUDGEMENT);
            if (reason != HumanValueReason.PHYSICAL_PRESENCE && value(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE) >= HIGH)
                supporting.add(HumanValueReason.PHYSICAL_PRESENCE);
            if (reason != HumanValueReason.ACCOUNTABILITY && item.needsCloseHumanOversight())
                supporting.add(HumanValueReason.ACCOUNTABILITY);
            result.add(new HumanValueArea(result.size() + 1, item.responsibilityIndex(), item.responsibilityKey(),
                    item.responsibilityText(), item.category(), reason, supporting, reason.explanation(),
                    humanValueScore(item), "HV_" + reason.name(), item.closeCall()));
        }
        return result;
    }

    private static HumanValueReason reason(ResponsibilityAnalysisItem item) {
        double interaction = value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION);
        double context = value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT);
        double physical = value(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE);
        boolean physicalCounts = item.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON || physical >= HIGH;
        HumanValueReason best = HumanValueReason.HUMAN_INTERACTION;
        double bestValue = interaction;
        if (context > bestValue) { best = HumanValueReason.CONTEXTUAL_JUDGEMENT; bestValue = context; }
        if (physicalCounts && physical > bestValue) { best = HumanValueReason.PHYSICAL_PRESENCE; bestValue = physical; }
        return bestValue >= HIGH ? best : HumanValueReason.ACCOUNTABILITY;
    }

    private static double humanValueScore(ResponsibilityAnalysisItem item) {
        HumanValueReason reason = reason(item);
        double base = switch (reason) {
            case HUMAN_INTERACTION -> value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION);
            case CONTEXTUAL_JUDGEMENT -> value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT);
            case PHYSICAL_PRESENCE -> value(item, ResponsibilityJudgmentKind.PHYSICAL_PRESENCE);
            case ACCOUNTABILITY -> value(item, ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY);
        };
        boolean humanCategory = item.category() == ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING
                || item.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON;
        return base + (humanCategory ? 1.0 : 0.0) + (item.needsCloseHumanOversight() ? 0.25 : 0.0)
                - (item.closeCall() ? CLOSE_CALL_PENALTY : 0.0);
    }

    // ---- Skills ----

    private List<SkillPriority> skills(List<ResponsibilityAnalysisItem> items, List<AiAssistanceOpportunity> opportunities,
            double regulated) {
        Map<SkillFamily, List<String>> triggered = new EnumMap<>(SkillFamily.class);
        List<String> opportunityKeys = opportunities.stream().map(AiAssistanceOpportunity::responsibilityKey).toList();
        if (!opportunities.isEmpty()) triggered.put(SkillFamily.VERIFICATION_AND_QUALITY_CHECKING, opportunityKeys);
        if (opportunities.size() >= 2) triggered.put(SkillFamily.AI_ASSISTED_WORKFLOW, opportunityKeys);
        List<String> judgement = keys(items, item -> value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT) >= STRONG_HUMAN_DEMAND);
        if (judgement.size() >= 2) triggered.put(SkillFamily.JUDGEMENT_AND_DECISION_MAKING, judgement);
        List<String> communication = keys(items, item -> value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION) >= STRONG_HUMAN_DEMAND);
        if (!communication.isEmpty()) triggered.put(SkillFamily.COMMUNICATION_AND_RELATIONSHIPS, communication);
        List<String> oversight = keys(items, ResponsibilityAnalysisItem::needsCloseHumanOversight);
        if (regulated >= MODERATE || oversight.size() * 2 >= items.size()) {
            triggered.put(SkillFamily.RESPONSIBLE_AI_USE, oversight.isEmpty() ? keys(items, item -> true) : oversight);
        }
        List<String> handsOn = keys(items, item -> item.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON);
        if (handsOn.size() >= 2) triggered.put(SkillFamily.HANDS_ON_PRACTICE_AND_SAFETY, handsOn);

        boolean regulatedHigh = regulated >= HIGH;
        List<SkillFamily> ranked = triggered.keySet().stream().sorted(Comparator
                .comparingInt((SkillFamily family) -> family == SkillFamily.RESPONSIBLE_AI_USE && regulatedHigh ? 0 : 1)
                .thenComparingInt(family -> -triggered.get(family).size())
                .thenComparingInt(SkillFamily::ordinal)).limit(MAXIMUM_SKILL_PRIORITIES).toList();
        List<SkillPriority> result = new ArrayList<>();
        for (SkillFamily family : ranked) {
            result.add(new SkillPriority(result.size() + 1, family, family.label(), skillReason(family),
                    triggered.get(family), "SK_" + family.name()));
        }
        return result;
    }

    private static String skillReason(SkillFamily family) {
        return switch (family) {
            case RESPONSIBLE_AI_USE -> "The work operates under formal obligations or needs close human oversight, so how "
                    + "AI is used matters as much as whether it is used.";
            case VERIFICATION_AND_QUALITY_CHECKING -> "Checking AI-assisted work is what keeps it trustworthy wherever you use it.";
            case AI_ASSISTED_WORKFLOW -> "Several of your responsibilities have clear scope for AI assistance under your review.";
            case JUDGEMENT_AND_DECISION_MAKING -> "Several responsibilities depend heavily on judgement in changing or "
                    + "incomplete situations.";
            case COMMUNICATION_AND_RELATIONSHIPS -> "Trust and communication are central to some of your responsibilities; "
                    + "continue strengthening them.";
            case HANDS_ON_PRACTICE_AND_SAFETY -> "Much of your work is hands-on or in person, so practical expertise and safe "
                    + "practice stay central.";
        };
    }

    // ---- Safeguards ----

    private List<ResponsibleUseSafeguard> safeguards(List<ResponsibilityAnalysisItem> items,
            List<AiAssistanceOpportunity> opportunities, double regulated) {
        List<ResponsibleUseSafeguard> result = new ArrayList<>();
        List<String> opportunityKeys = opportunities.stream().map(AiAssistanceOpportunity::responsibilityKey).toList();
        String regulatedLevel = ProfessionalResponsibilityPolicyV1.level(regulated).name();
        List<String> handsOnVeryHigh = keys(items, item -> item.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON
                && item.oversightRule().filter("O1_ACCOUNTABILITY_VERY_HIGH"::equals).isPresent());
        if (!handsOnVeryHigh.isEmpty()) {
            result.add(safeguard(SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK, SafeguardImportance.CRITICAL,
                    "Hands-on responsibilities carry very high accountability for errors.", handsOnVeryHigh,
                    List.of("category=MAINLY_HANDS_ON_OR_IN_PERSON", "oversight=O1_ACCOUNTABILITY_VERY_HIGH")));
        }
        List<String> veryHigh = keys(items, item -> item.oversightRule().filter("O1_ACCOUNTABILITY_VERY_HIGH"::equals).isPresent());
        if (!veryHigh.isEmpty()) {
            result.add(safeguard(SafeguardType.ESCALATE_UNCERTAINTY, SafeguardImportance.CRITICAL,
                    "Errors in some responsibilities could have serious consequences.", veryHigh,
                    List.of("oversight=O1_ACCOUNTABILITY_VERY_HIGH")));
        }
        if (!opportunities.isEmpty()) {
            result.add(safeguard(SafeguardType.APPROVED_TOOLS_AND_CONFIDENTIALITY,
                    regulated >= MODERATE ? SafeguardImportance.HIGH : SafeguardImportance.STANDARD,
                    regulated >= MODERATE ? "The work is meaningfully governed by formal procedures, confidentiality or "
                            + "regulation." : "AI assistance is suggested for work that may involve organisational information.",
                    opportunityKeys, List.of("regulatedContext=" + regulatedLevel)));
            boolean oversight = opportunities.stream().anyMatch(AiAssistanceOpportunity::needsCloseHumanOversight);
            result.add(safeguard(SafeguardType.HUMAN_REVIEW_OF_AI_OUTPUT,
                    oversight ? SafeguardImportance.HIGH : SafeguardImportance.STANDARD,
                    oversight ? "Some suggested uses of AI are in responsibilities that need close human oversight."
                            : "Every suggested use of AI assumes your review.", opportunityKeys,
                    oversight ? List.of("opportunityOversight=true") : List.of("opportunityOversight=false")));
        }
        List<String> judgementKeys = opportunities.stream().filter(opportunity ->
                opportunity.category() != ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY
                        || opportunity.constraints().contains(SafeUseConstraint.KEEP_DECISIONS_HUMAN))
                .map(AiAssistanceOpportunity::responsibilityKey).toList();
        if (!judgementKeys.isEmpty()) {
            result.add(safeguard(SafeguardType.KEEP_DECISIONS_HUMAN, SafeguardImportance.STANDARD,
                    "Some suggested uses of AI sit inside responsibilities where the judgement is yours.", judgementKeys,
                    List.of("opportunityCategory=AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT or contextualJudgement=HIGH")));
        }
        List<String> accountable = opportunities.stream().filter(opportunity -> items.stream()
                        .filter(item -> item.responsibilityKey().equals(opportunity.responsibilityKey()))
                        .anyMatch(item -> value(item, ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY) >= HIGH))
                .map(AiAssistanceOpportunity::responsibilityKey).toList();
        if (!accountable.isEmpty()) {
            result.add(safeguard(SafeguardType.VERIFY_FACTS_FIGURES_AND_SOURCES, SafeguardImportance.STANDARD,
                    "Errors in these responsibilities carry meaningful accountability.", accountable,
                    List.of("outcomeAccountability=HIGH")));
        }
        return result.stream().sorted(Comparator.comparingInt(safeguard -> safeguard.importance().ordinal()))
                .limit(MAXIMUM_SAFEGUARDS).toList();
    }

    private static ResponsibleUseSafeguard safeguard(SafeguardType type, SafeguardImportance importance, String reason,
            List<String> keys, List<String> signals) {
        return new ResponsibleUseSafeguard(type, type.guidance(), importance, reason, keys, signals, "SG_" + type.name());
    }

    // ---- 90-day plan ----

    private NinetyDayPlan plan(List<ResponsibilityAnalysisItem> items, List<AiAssistanceOpportunity> opportunities,
            List<HumanValueArea> humanValue, List<SkillPriority> skills, List<ResponsibleUseSafeguard> safeguards,
            double regulated) {
        boolean anyOversight = items.stream().anyMatch(ResponsibilityAnalysisItem::needsCloseHumanOversight);
        boolean strict = safeguards.stream().anyMatch(safeguard -> safeguard.importance() != SafeguardImportance.STANDARD);
        // The pilot starts where errors matter least: no oversight, then O2, then O1, each class by opportunity rank.
        // An O1 opportunity is the first pilot only when no safer eligible opportunity exists.
        AiAssistanceOpportunity top = opportunities.stream()
                .min(Comparator.comparingInt((AiAssistanceOpportunity opportunity) -> oversightLevel(items, opportunity))
                        .thenComparingInt(AiAssistanceOpportunity::rank)).orElse(null);
        AiAssistanceOpportunity next = opportunities.stream().filter(opportunity -> opportunity != top).findFirst().orElse(null);
        List<AiAssistanceOpportunity> firstTwo = top == null ? List.of()
                : next == null ? List.of(top) : List.of(top, next);
        List<HumanValueArea> topHuman = humanValue.stream().limit(2).toList();
        SkillPriority humanSkill = skills.stream().filter(skill -> skill.family() == SkillFamily.JUDGEMENT_AND_DECISION_MAKING
                || skill.family() == SkillFamily.COMMUNICATION_AND_RELATIONSHIPS
                || skill.family() == SkillFamily.HANDS_ON_PRACTICE_AND_SAFETY).findFirst().orElse(null);

        List<PlanAction> first = new ArrayList<>();
        if (regulated >= MODERATE || anyOversight) {
            first.add(action("P1_CONFIRM_POLICY_AND_TOOLS", PlanPhase.DAYS_1_TO_30,
                    "Confirm your organisation's policy and approved tools before trying any AI assistance.",
                    "Formal obligations or close oversight apply to parts of this work.", List.of(),
                    "Use only approved tools for any work-related information."));
        }
        if (top != null) {
            first.add(action("P1_MAP_CURRENT_WORKFLOW", PlanPhase.DAYS_1_TO_30,
                    "Map how you currently carry out " + quoted(firstTwo.stream().map(AiAssistanceOpportunity::responsibilityText).toList())
                            + ", noting which steps involve preparing, drafting, checking or organising information.",
                    "These are the responsibilities with the clearest scope for AI assistance.", keysOf(firstTwo), ""));
            first.add(action("P1_DEFINE_HUMAN_REVIEW", PlanPhase.DAYS_1_TO_30,
                    "Write down what must always be checked or decided by you in those responsibilities.",
                    "Assistance stays useful only where its limits are explicit.", keysOf(firstTwo),
                    firstTwo.stream().anyMatch(AiAssistanceOpportunity::needsCloseHumanOversight)
                            ? "These responsibilities need close human oversight." : ""));
        }
        if (!topHuman.isEmpty()) {
            first.add(action("P1_NOTE_HUMAN_CONTRIBUTION", PlanPhase.DAYS_1_TO_30,
                    "Note where " + quoted(topHuman.stream().map(HumanValueArea::responsibilityText).toList())
                            + " depend on your judgement, relationships or presence, so any AI use stays around them.",
                    "These are the responsibilities where your contribution matters most.",
                    topHuman.stream().map(HumanValueArea::responsibilityKey).toList(), ""));
        }
        if (first.size() < 2) {
            first.add(action("P1_REVIEW_RESPONSIBILITIES", PlanPhase.DAYS_1_TO_30,
                    "Review your responsibilities for routine information work, such as notes, summaries or records, that "
                            + "could later be supported under your review.",
                    "No responsibility stood out as a clear opportunity, so start by looking.", keys(items, item -> true), ""));
        }

        List<PlanAction> second = new ArrayList<>();
        if (top != null) {
            second.add(action("P2_PILOT_WITH_REVIEW", PlanPhase.DAYS_31_TO_60,
                    "Try AI assistance on " + quoted(List.of(top.responsibilityText())) + " for a few weeks, reviewing every "
                            + "output, and compare time, quality and errors with your usual approach.",
                    "It is the highest-ranked opportunity with the least need for oversight, so a trial carries the least risk.",
                    List.of(top.responsibilityKey()),
                    top.needsCloseHumanOversight() ? "Review every output: this responsibility needs close human oversight."
                            : "Review every output before it is used."));
            if (top.category() != ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY) {
                second.add(action("P2_LIMIT_TO_PREPARATORY_PARTS", PlanPhase.DAYS_31_TO_60,
                        "Limit the trial to preparatory parts and keep the judgement in it yours.",
                        "The responsibility combines assistance scope with judgement that stays human.",
                        List.of(top.responsibilityKey()), ""));
            }
            second.add(action("P2_RECORD_WHAT_WORKS", PlanPhase.DAYS_31_TO_60,
                    "Record where AI help was useful, where it was wrong and what you had to correct.",
                    "Evidence from your own work decides what to keep.", List.of(top.responsibilityKey()), ""));
        }
        if (humanSkill != null) {
            second.add(action("P2_PRACTISE_HUMAN_SKILL", PlanPhase.DAYS_31_TO_60,
                    "Practise " + lowerFirst(humanSkill.label()) + " deliberately in "
                            + quoted(texts(items, humanSkill.supportingResponsibilityKeys().stream().limit(2).toList())) + ".",
                    humanSkill.reason(), humanSkill.supportingResponsibilityKeys().stream().limit(2).toList(), ""));
        }
        if (second.size() < 2 && !topHuman.isEmpty()) {
            second.add(action("P2_SEEK_FEEDBACK", PlanPhase.DAYS_31_TO_60,
                    "Ask for feedback on how you handle " + quoted(List.of(topHuman.getFirst().responsibilityText())) + ".",
                    "It is where your contribution matters most.", List.of(topHuman.getFirst().responsibilityKey()), ""));
        }

        List<PlanAction> third = new ArrayList<>();
        if (top != null) {
            third.add(action("P3_ADOPT_OR_STOP", PlanPhase.DAYS_61_TO_90,
                    "Keep what worked as a checklist or template, and stop anything that did not save time or protect quality.",
                    "Only practice that proved useful should become routine.", List.of(top.responsibilityKey()), ""));
        }
        if (strict) {
            third.add(action("P3_DOCUMENT_SAFEGUARDS", PlanPhase.DAYS_61_TO_90,
                    "Write down the safeguards you follow, such as review, approved tools and escalation, so they are repeatable.",
                    "Parts of this work need strong safeguards.", List.of(), ""));
        }
        if (next != null) {
            third.add(action("P3_CHOOSE_NEXT_OPPORTUNITY", PlanPhase.DAYS_61_TO_90,
                    "Choose the next responsibility to try: " + quoted(List.of(next.responsibilityText())) + ".",
                    "It is the next-ranked opportunity in your analysis.", List.of(next.responsibilityKey()),
                    next.needsCloseHumanOversight() ? "This responsibility needs close human oversight." : ""));
        }
        if (!topHuman.isEmpty() && third.size() < MAXIMUM_ACTIONS_PER_PHASE) {
            third.add(action("P3_STRENGTHEN_HUMAN_CONTRIBUTION", PlanPhase.DAYS_61_TO_90,
                    "Keep strengthening " + (humanSkill == null ? "the human side of your work" : lowerFirst(humanSkill.label()))
                            + " where your contribution matters most: "
                            + quoted(topHuman.stream().map(HumanValueArea::responsibilityText).toList()) + ".",
                    "These responsibilities depend most on you.", topHuman.stream().map(HumanValueArea::responsibilityKey).toList(), ""));
        }
        if (third.size() < 2) {
            third.add(action("P3_REVIEW_PROGRESS", PlanPhase.DAYS_61_TO_90,
                    "Review what you have learned and decide whether any responsibility is worth a careful trial of AI assistance.",
                    "A short review keeps the next step deliberate.", List.of(), ""));
        }
        return new NinetyDayPlan(cap(first), cap(second), cap(third));
    }

    /** 0 without oversight, 1 for oversight from regulated context, 2 for very high accountability. */
    private static int oversightLevel(List<ResponsibilityAnalysisItem> items, AiAssistanceOpportunity opportunity) {
        return items.stream().filter(item -> item.responsibilityKey().equals(opportunity.responsibilityKey())).findFirst()
                .flatMap(ResponsibilityAnalysisItem::oversightRule)
                .map(rule -> rule.equals("O1_ACCOUNTABILITY_VERY_HIGH") ? 2 : 1).orElse(0);
    }

    private static List<PlanAction> cap(List<PlanAction> actions) {
        return actions.stream().limit(MAXIMUM_ACTIONS_PER_PHASE).toList();
    }

    private static PlanAction action(String id, PlanPhase phase, String title, String rationale, List<String> keys,
            String reviewNote) {
        return new PlanAction(id, phase, title, rationale, keys, reviewNote, id);
    }

    // ---- Helpers ----

    private static double value(ResponsibilityAnalysisItem item, ResponsibilityJudgmentKind kind) {
        return item.judgments().get(kind).value();
    }

    private static String level(ResponsibilityAnalysisItem item, ResponsibilityJudgmentKind kind) {
        return item.levels().get(kind).label().toLowerCase(java.util.Locale.ROOT);
    }

    private static double humanDemand(ResponsibilityAnalysisItem item) {
        return Math.max(value(item, ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT),
                value(item, ResponsibilityJudgmentKind.HUMAN_INTERACTION));
    }

    private static List<String> keys(List<ResponsibilityAnalysisItem> items,
            java.util.function.Predicate<ResponsibilityAnalysisItem> filter) {
        return items.stream().filter(filter).map(ResponsibilityAnalysisItem::responsibilityKey).toList();
    }

    private static List<String> keysOf(List<AiAssistanceOpportunity> opportunities) {
        return opportunities.stream().map(AiAssistanceOpportunity::responsibilityKey).toList();
    }

    private static List<String> texts(List<ResponsibilityAnalysisItem> items, List<String> keys) {
        return keys.stream().map(key -> items.stream().filter(item -> item.responsibilityKey().equals(key)).findFirst()
                .orElseThrow().responsibilityText()).toList();
    }

    private static String quoted(List<String> texts) {
        List<String> quoted = texts.stream().map(text -> "“" + text + "”").toList();
        return quoted.size() <= 1 ? String.join("", quoted)
                : String.join(", ", quoted.subList(0, quoted.size() - 1)) + " and " + quoted.getLast();
    }

    private static String lowerFirst(String label) {
        return label.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + label.substring(1);
    }
}
