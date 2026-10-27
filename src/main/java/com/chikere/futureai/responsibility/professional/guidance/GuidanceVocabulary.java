package com.chikere.futureai.responsibility.professional.guidance;

/**
 * The fixed vocabulary of the guidance policy. Every customer-facing phrase is cautious: AI assistance is
 * described as support for parts of the work under the person's review, never as automating or replacing it.
 */
public final class GuidanceVocabulary {

    private GuidanceVocabulary() {
    }

    /** How AI assistance is framed for a responsibility. */
    public enum OpportunityType {
        PREPARATION_AND_INFORMATION_SUPPORT("Use AI to support preparation, drafting, checking or information handling, with you reviewing the result."),
        SUPPORT_FOR_PARTS_UNDER_YOUR_JUDGEMENT("Consider AI assistance for parts of this responsibility, while the judgement stays yours."),
        PREPARATION_ONLY_FOR_HUMAN_LED_WORK("AI may help organise information beforehand; the responsibility itself stays human-led.");

        private final String guidance;

        OpportunityType(String guidance) {
            this.guidance = guidance;
        }

        public String guidance() {
            return guidance;
        }
    }

    /** A condition attached to one opportunity. */
    public enum SafeUseConstraint {
        REVIEW_EVERY_OUTPUT("Review every AI-assisted output before it is used."),
        APPROVED_TOOLS_ONLY("Use only tools your organisation has approved for this kind of information."),
        KEEP_DECISIONS_HUMAN("Keep the decisions and conclusions yours.");

        private final String guidance;

        SafeUseConstraint(String guidance) {
            this.guidance = guidance;
        }

        public String guidance() {
            return guidance;
        }
    }

    /** Why a responsibility is a human-value area. */
    public enum HumanValueReason {
        HUMAN_INTERACTION("Trust, communication and relationship handling are central here."),
        CONTEXTUAL_JUDGEMENT("This responsibility depends heavily on interpreting changing or incomplete situations."),
        PHYSICAL_PRESENCE("The work itself depends substantially on physical action or in-person presence."),
        ACCOUNTABILITY("Human review and accountability remain especially important because errors can have significant consequences.");

        private final String explanation;

        HumanValueReason(String explanation) {
            this.explanation = explanation;
        }

        public String explanation() {
            return explanation;
        }
    }

    /** Skill families, in tie-break order. None names a course, product or vendor. */
    public enum SkillFamily {
        RESPONSIBLE_AI_USE("Responsible and approved use of AI"),
        VERIFICATION_AND_QUALITY_CHECKING("Verification and quality checking"),
        AI_ASSISTED_WORKFLOW("Using AI assistance within your workflow"),
        JUDGEMENT_AND_DECISION_MAKING("Judgement and decision-making under uncertainty"),
        COMMUNICATION_AND_RELATIONSHIPS("Communication and relationship skills"),
        HANDS_ON_PRACTICE_AND_SAFETY("Hands-on expertise and safe practice");

        private final String label;

        SkillFamily(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum SafeguardType {
        NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK("Do not rely on AI for safety-critical hands-on work; use it only around documentation and preparation."),
        ESCALATE_UNCERTAINTY("Where an error could have serious consequences, escalate uncertainty rather than relying on AI output."),
        APPROVED_TOOLS_AND_CONFIDENTIALITY("Follow your employer's and profession's policies, and do not put confidential or personal information into tools that are not approved for it."),
        HUMAN_REVIEW_OF_AI_OUTPUT("Review AI-assisted work yourself before it is used or shared."),
        KEEP_DECISIONS_HUMAN("Keep ownership of decisions and conclusions; treat AI output as input, not as the answer."),
        VERIFY_FACTS_FIGURES_AND_SOURCES("Check facts, figures and sources in AI-assisted work against the originals.");

        private final String guidance;

        SafeguardType(String guidance) {
            this.guidance = guidance;
        }

        public String guidance() {
            return guidance;
        }
    }

    public enum SafeguardImportance {
        CRITICAL,
        HIGH,
        STANDARD
    }

    public enum PlanPhase {
        DAYS_1_TO_30("Days 1-30: observe and prepare"),
        DAYS_31_TO_60("Days 31-60: run controlled experiments"),
        DAYS_61_TO_90("Days 61-90: standardise useful practice");

        private final String label;

        PlanPhase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
