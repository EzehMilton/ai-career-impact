package com.chikere.futureai.responsibility.professional.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Customer-facing wording for a stored responsibility classification: one sentence per classification rule and a
 * short label for each High judgment. Presentation only; nothing here classifies, ranks or re-reads a threshold.
 * Used by the paid report and, as the same wording, by the paid narrative prompt.
 */
public final class ResponsibilityExplanations {

    private static final Map<String, String> BY_RULE = Map.of(
            "R1_PHYSICAL_DOMINANT", "This responsibility depends mainly on physical action or being present in person, "
                    + "so any AI role is limited to the paperwork and preparation around it.",
            "R2_HUMAN_DEMAND_DOMINATES", "Human interaction or judgement is more central here than the scope for AI "
                    + "assistance, so AI is best kept to supporting preparation around your own work.",
            "R3_ASSISTANCE_LOW", "The scope for AI assistance with this responsibility appears limited, so it remains "
                    + "mainly your own work.",
            "R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH", "Much of this responsibility involves information, drafting or "
                    + "checking, where AI may assist while you review the result.",
            "R5_PARTIAL_ASSISTANCE", "AI may assist with some parts of this responsibility, while other parts depend on "
                    + "your judgement or your interaction with people.");

    private static final Map<ResponsibilityJudgmentKind, String> HIGH_SIGNAL = Map.of(
            ResponsibilityJudgmentKind.ASSISTANCE_SCOPE, "Broad scope for AI assistance",
            ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT, "Depends on contextual judgement",
            ResponsibilityJudgmentKind.HUMAN_INTERACTION, "Centred on human interaction",
            ResponsibilityJudgmentKind.PHYSICAL_PRESENCE, "Needs physical or in-person presence",
            ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY, "Carries significant accountability");

    public static final int MAXIMUM_SIGNALS = 3;

    private ResponsibilityExplanations() {
    }

    /** The sentence for a stored classification rule; an unknown (future) rule falls back to the category label. */
    public static String forRule(String classificationRule, ResponsibilityCategory category) {
        return BY_RULE.getOrDefault(classificationRule, category.label() + ".");
    }

    /**
     * Up to three labels for the judgments stored as High, in the fixed judgment order. For human-led and hands-on
     * work a High assistance scope is left out: beside that category it would read as a contradiction, and it is not
     * what distinguishes the responsibility.
     */
    public static List<String> highSignals(Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels,
            ResponsibilityCategory category) {
        boolean assistanceLed = category == ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY
                || category == ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT;
        List<String> signals = new ArrayList<>();
        for (ResponsibilityJudgmentKind kind : ResponsibilityJudgmentKind.values()) {
            if (kind == ResponsibilityJudgmentKind.ASSISTANCE_SCOPE && !assistanceLed) {
                continue;
            }
            if (levels.get(kind) == ResponsibilityLevel.HIGH && signals.size() < MAXIMUM_SIGNALS) {
                signals.add(HIGH_SIGNAL.get(kind));
            }
        }
        return List.copyOf(signals);
    }
}
