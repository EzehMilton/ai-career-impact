package com.chikere.futureai.decision.professional;

/**
 * The plain-language sentence for each factor direction, shown to visitors in reports and given to the
 * narrative model so its explanation uses the same description. One source, so the two cannot drift.
 */
public final class ProfessionalFactorDescriptions {

    private ProfessionalFactorDescriptions() {
    }

    public static String describe(ProfessionalFactorDirection direction) {
        return switch (direction) {
            case HIGH_TASK_REPEATABILITY -> "Some of the work follows set routines or established processes.";
            case LOW_TASK_REPEATABILITY -> "The work is less repetitive or standardised";
            case HIGH_DIGITAL_EXECUTABILITY -> "Much of the work can be performed digitally";
            case LOW_DIGITAL_EXECUTABILITY -> "The work cannot be done entirely through digital systems";
            case HIGH_HUMAN_TRUST_DEPENDENCY -> "Trust, persuasion or relationships matter to successful performance";
            case LOW_HUMAN_TRUST_DEPENDENCY -> "The work depends less on human trust or relationships";
            case HIGH_PHYSICAL_WORLD_DEPENDENCY -> "The work relies on skilled physical-world presence";
            case LOW_PHYSICAL_WORLD_DEPENDENCY -> "The work requires little physical-world presence";
            case HIGH_CONTEXTUAL_COMPLEXITY -> "The work depends on understanding complex or changing situations";
            case LOW_CONTEXTUAL_COMPLEXITY -> "The work usually follows clear and predictable patterns";
            case HIGH_ORIGINAL_JUDGMENT -> "The role relies on original judgement and synthesis";
            case LOW_ORIGINAL_JUDGMENT ->
                    "Some of the work follows established approaches rather than requiring completely new approaches.";
            case HIGH_ACCOUNTABILITY_RESPONSIBILITY -> "The role carries responsibility for consequential decisions";
            case LOW_ACCOUNTABILITY_RESPONSIBILITY -> "The role carries limited responsibility for consequential decisions";
        };
    }
}
