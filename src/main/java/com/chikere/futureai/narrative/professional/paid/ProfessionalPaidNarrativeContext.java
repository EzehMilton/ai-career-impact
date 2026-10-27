package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalScoreDisplayFormatter;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityExplanations;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything the paid narrative may explain, taken only from stored artefacts: the frozen role-level paid report,
 * the responsibility-analysis snapshot and the guidance snapshot. {@link #prompt()} is the compact view sent to the
 * provider; the identifier lists are what the validator holds the response to, in the deterministic order.
 */
public record ProfessionalPaidNarrativeContext(
        UUID assessmentId,
        UUID analysisId,
        UUID guidanceId,
        String profession,
        Prompt prompt,
        List<String> opportunityIds,
        List<String> humanValueIds,
        List<String> skillIds,
        List<String> planActionIds,
        Map<String, ResponsibilityCategory> categoryById,
        List<String> storedSkillLabels,
        List<String> unselectedSkillLabels) {

    static final int MAXIMUM_WORK_DESCRIPTION = 800;

    public ProfessionalPaidNarrativeContext {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(analysisId);
        Objects.requireNonNull(guidanceId);
        Objects.requireNonNull(profession);
        Objects.requireNonNull(prompt);
        opportunityIds = List.copyOf(opportunityIds);
        humanValueIds = List.copyOf(humanValueIds);
        skillIds = List.copyOf(skillIds);
        planActionIds = List.copyOf(planActionIds);
        categoryById = Map.copyOf(categoryById);
        storedSkillLabels = List.copyOf(storedSkillLabels);
        unselectedSkillLabels = List.copyOf(unselectedSkillLabels);
    }

    public static ProfessionalPaidNarrativeContext from(ProfessionalReport roleReport,
            ProfessionalResponsibilityAnalysisSnapshot analysis, ProfessionalResponsibilityGuidanceSnapshot guidance) {
        Objects.requireNonNull(roleReport);
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(guidance);
        if (!guidance.analysisSnapshotId().equals(analysis.snapshotId())
                || !guidance.assessmentId().equals(analysis.assessmentId())) {
            throw new IllegalArgumentException("the guidance must explain this analysis");
        }
        var plan = guidance.guidance();
        Map<String, String> texts = new LinkedHashMap<>();
        analysis.items().forEach(item -> texts.put(item.responsibilityKey(), item.responsibilityText()));

        List<Prompt.Measure> measures = roleReport.measures().stream().map(measure -> new Prompt.Measure(
                measureName(measure.measure().name()), ProfessionalScoreDisplayFormatter.format(measure.indicator(),
                measure.band()), label(measure.band().name()), measure.conciseExplanation())).toList();
        List<Prompt.Responsibility> responsibilities = analysis.items().stream().map(item -> new Prompt.Responsibility(
                item.responsibilityKey(), item.responsibilityText(), item.category().label(),
                levels(item.levels(), com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel.HIGH),
                levels(item.levels(), com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel.MODERATE),
                item.needsCloseHumanOversight(),
                ResponsibilityExplanations.forRule(item.classificationRule(), item.category()))).toList();

        List<Prompt.Opportunity> opportunities = new ArrayList<>();
        Map<String, ResponsibilityCategory> categories = new LinkedHashMap<>();
        for (AiAssistanceOpportunity opportunity : plan.opportunities()) {
            String id = opportunityId(opportunity);
            categories.put(id, opportunity.category());
            opportunities.add(new Prompt.Opportunity(id, opportunity.responsibilityKey(), opportunity.responsibilityText(),
                    opportunity.category().label(), opportunity.guidance(),
                    opportunity.constraints().stream().map(SafeUseConstraint::guidance).toList(),
                    opportunity.needsCloseHumanOversight()));
        }
        List<Prompt.HumanValue> humanValue = new ArrayList<>();
        for (HumanValueArea area : plan.humanValueAreas()) {
            String id = humanValueId(area);
            categories.put(id, area.category());
            humanValue.add(new Prompt.HumanValue(id, area.responsibilityKey(), area.responsibilityText(),
                    area.category().label(), reasonLabel(area.reason().name()), area.explanation()));
        }
        List<Prompt.Skill> skills = plan.skillPriorities().stream().map(skill -> new Prompt.Skill(skillId(skill),
                skill.label(), skill.reason(), skill.supportingResponsibilityKeys().stream()
                        .map(key -> texts.getOrDefault(key, key)).toList())).toList();
        List<Prompt.Phase> phases = new ArrayList<>();
        List<String> actionIds = new ArrayList<>();
        List<List<PlanAction>> planPhases = List.of(plan.plan().days1To30(), plan.plan().days31To60(),
                plan.plan().days61To90());
        for (int index = 0; index < planPhases.size(); index++) {
            List<Prompt.Action> actions = new ArrayList<>();
            for (PlanAction action : planPhases.get(index)) {
                String id = actionId(index + 1, action);
                actionIds.add(id);
                actions.add(new Prompt.Action(id, action.title()));
            }
            String phaseLabel = planPhases.get(index).isEmpty() ? "Phase " + (index + 1)
                    : planPhases.get(index).getFirst().phase().label();
            phases.add(new Prompt.Phase(phaseLabel, actions));
        }
        var prompt = new Prompt(roleReport.profession(),
                truncate(analysis.input().workDescription()), measures, roleReport.exposureDrivers(),
                roleReport.protectiveFactors(), roleReport.narrative().whatThisMeans().orElse(null), responsibilities,
                opportunities, humanValue, skills,
                plan.safeguards().stream().map(safeguard -> safeguard.guidance()).toList(), phases);
        List<String> storedSkills = plan.skillPriorities().stream().map(SkillPriority::label).toList();
        List<String> unselected = Arrays.stream(SkillFamily.values()).map(SkillFamily::label)
                .filter(label -> !storedSkills.contains(label)).toList();
        return new ProfessionalPaidNarrativeContext(analysis.assessmentId(), analysis.snapshotId(), guidance.guidanceId(),
                roleReport.profession(), prompt, opportunities.stream().map(Prompt.Opportunity::id).toList(),
                humanValue.stream().map(Prompt.HumanValue::id).toList(), skills.stream().map(Prompt.Skill::id).toList(),
                actionIds, categories, storedSkills, unselected);
    }

    public static String opportunityId(AiAssistanceOpportunity opportunity) {
        return "opportunity-" + opportunity.rank() + "-" + opportunity.responsibilityKey();
    }

    public static String humanValueId(HumanValueArea area) {
        return "human-value-" + area.rank() + "-" + area.responsibilityKey();
    }

    public static String skillId(SkillPriority skill) {
        return "skill-" + skill.rank() + "-" + skill.family().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** {@code phase} is 1, 2 or 3. */
    public static String actionId(int phase, PlanAction action) {
        return "action-" + phase + "-" + action.actionId().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The responsibility texts the validator may see quoted; they are user data, not the model's own words. */
    public List<String> responsibilityTexts() {
        return prompt.responsibilities().stream().map(Prompt.Responsibility::text).toList();
    }

    private static List<String> levels(Map<ResponsibilityJudgmentKind,
            com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel> levels,
            com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel wanted) {
        List<String> names = new ArrayList<>();
        for (ResponsibilityJudgmentKind kind : ResponsibilityJudgmentKind.values()) {
            if (levels.get(kind) == wanted) {
                names.add(switch (kind) {
                    case ASSISTANCE_SCOPE -> "scope for AI assistance";
                    case CONTEXTUAL_JUDGEMENT -> "contextual judgement";
                    case HUMAN_INTERACTION -> "human interaction";
                    case PHYSICAL_PRESENCE -> "physical or in-person presence";
                    case OUTCOME_ACCOUNTABILITY -> "accountability for outcomes";
                });
            }
        }
        return names;
    }

    private static String truncate(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAXIMUM_WORK_DESCRIPTION ? flat : flat.substring(0, MAXIMUM_WORK_DESCRIPTION) + "...";
    }

    static String measureName(String name) {
        return switch (name) {
            case "AI_EXPOSURE" -> "AI Exposure";
            case "HUMAN_ADVANTAGE" -> "Human Advantage";
            case "AI_COMPLEMENTARITY" -> "AI Complementarity";
            case "ROLE_ADAPTABILITY" -> "Role Adaptability";
            default -> label(name);
        };
    }

    static String reasonLabel(String reason) {
        return switch (reason) {
            case "HUMAN_INTERACTION" -> "Human interaction";
            case "CONTEXTUAL_JUDGEMENT" -> "Contextual judgement";
            case "PHYSICAL_PRESENCE" -> "Physical or in-person work";
            case "ACCOUNTABILITY" -> "Accountability";
            default -> label(reason);
        };
    }

    private static String label(String value) {
        String text = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /**
     * The provider view: names, labels and texts only. No raw probability, provider confidence, clarity value,
     * rule identifier or threshold is included; scores appear only as the stored display values, for context.
     */
    public record Prompt(String profession, String workDescription, List<Measure> measures,
            List<String> factorsRaisingAiExposure, List<String> factorsLoweringAiExposure, String roleLevelNarrative,
            List<Responsibility> responsibilities, List<Opportunity> opportunities, List<HumanValue> humanValueAreas,
            List<Skill> skillPriorities, List<String> safeguards, List<Phase> ninetyDayPlan) {

        public record Measure(String name, String storedScore, String band, String meaning) {
        }

        public record Responsibility(String key, String text, String category, List<String> highSignals,
                List<String> moderateSignals, boolean needsCloseHumanOversight, String interpretation) {
        }

        public record Opportunity(String id, String responsibilityKey, String responsibility, String category,
                String howAiMayAssist, List<String> conditions, boolean needsCloseHumanOversight) {
        }

        public record HumanValue(String id, String responsibilityKey, String responsibility, String category,
                String reason, String reasonExplanation) {
        }

        public record Skill(String id, String label, String reason, List<String> supportingResponsibilities) {
        }

        public record Phase(String phase, List<Action> actions) {
        }

        public record Action(String id, String action) {
        }
    }
}
