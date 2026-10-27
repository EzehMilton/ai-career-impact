package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.HumanValueReason;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.OpportunityType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.PlanPhase;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardImportance;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The deterministic guidance derived from one stored responsibility analysis. Every item names the rule that
 * produced it and the responsibilities and signals behind it, so the report can explain it without a model.
 */
public record ProfessionalResponsibilityGuidance(
        String policyVersion,
        UUID analysisSnapshotId,
        List<AiAssistanceOpportunity> opportunities,
        List<HumanValueArea> humanValueAreas,
        List<SkillPriority> skillPriorities,
        NinetyDayPlan plan,
        List<ResponsibleUseSafeguard> safeguards) {

    public ProfessionalResponsibilityGuidance {
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(analysisSnapshotId);
        opportunities = List.copyOf(opportunities);
        humanValueAreas = List.copyOf(humanValueAreas);
        skillPriorities = List.copyOf(skillPriorities);
        Objects.requireNonNull(plan);
        safeguards = List.copyOf(safeguards);
    }

    /** One responsibility where AI assistance looks most promising, with the components of its ranking score. */
    public record AiAssistanceOpportunity(int rank, int responsibilityIndex, String responsibilityKey,
            String responsibilityText, ResponsibilityCategory category, boolean needsCloseHumanOversight,
            OpportunityType type, String guidance, List<SafeUseConstraint> constraints, double score,
            Map<String, Double> scoreComponents, String ruleId, boolean dependsOnCloseCall, String rationale) {
        public AiAssistanceOpportunity {
            constraints = List.copyOf(constraints);
            scoreComponents = Map.copyOf(scoreComponents);
        }
    }

    public record HumanValueArea(int rank, int responsibilityIndex, String responsibilityKey, String responsibilityText,
            ResponsibilityCategory category, HumanValueReason reason, List<HumanValueReason> supportingSignals,
            String explanation, double score, String ruleId, boolean dependsOnCloseCall) {
        public HumanValueArea {
            supportingSignals = List.copyOf(supportingSignals);
        }
    }

    public record SkillPriority(int rank, SkillFamily family, String label, String reason,
            List<String> supportingResponsibilityKeys, String ruleId) {
        public SkillPriority {
            supportingResponsibilityKeys = List.copyOf(supportingResponsibilityKeys);
        }
    }

    public record ResponsibleUseSafeguard(SafeguardType type, String guidance, SafeguardImportance importance,
            String reason, List<String> relatedResponsibilityKeys, List<String> signals, String ruleId) {
        public ResponsibleUseSafeguard {
            relatedResponsibilityKeys = List.copyOf(relatedResponsibilityKeys);
            signals = List.copyOf(signals);
        }
    }

    public record PlanAction(String actionId, PlanPhase phase, String title, String rationale,
            List<String> responsibilityKeys, String reviewNote, String ruleId) {
        public PlanAction {
            responsibilityKeys = List.copyOf(responsibilityKeys);
        }
    }

    public record NinetyDayPlan(List<PlanAction> days1To30, List<PlanAction> days31To60, List<PlanAction> days61To90) {
        public NinetyDayPlan {
            days1To30 = List.copyOf(days1To30);
            days31To60 = List.copyOf(days31To60);
            days61To90 = List.copyOf(days61To90);
        }
    }
}
