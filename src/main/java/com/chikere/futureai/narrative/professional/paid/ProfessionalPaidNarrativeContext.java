package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalScoreDisplayFormatter;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
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
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel;

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
 *
 * <p>The practical-example limits are derived generically from stored signals, never from the profession: an
 * opportunity is <em>high-stakes</em> when its responsibility carries High accountability, contextual judgement,
 * human interaction or physical presence, needs close human oversight, or sits in an at least Moderate regulated
 * context; it is <em>safety-critical</em> when it carries the very-high-accountability oversight marker or the
 * guidance holds the safety-critical hands-on safeguard. Each class narrows what the model may illustrate.</p>
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
        List<String> unselectedSkillLabels,
        List<String> highStakesOpportunityIds,
        List<String> safetyCriticalOpportunityIds,
        Map<String, String> bandByMeasure) {

    static final int MAXIMUM_WORK_DESCRIPTION = 800;

    /** What a high-stakes opportunity's practical examples may cover. Generic: no occupation is named. */
    public static final String HIGH_STAKES_LIMIT = "Keep practical examples to preparation, documentation, organising "
            + "and summarising information, checking for gaps or inconsistencies, learning and administrative support. "
            + "AI output is a draft or prompt for the person's review; never describe AI making, recommending or approving "
            + "the final decision, conclusion, approval or sign-off.";

    /** The stricter limit for safety-critical, very-high-accountability work, added to {@link #HIGH_STAKES_LIMIT}. */
    public static final String SAFETY_CRITICAL_LIMIT = "This is safety-critical, very-high-accountability work: never "
            + "describe AI assessing a person or situation directly, diagnosing, deciding or recommending treatment, "
            + "prescribing, setting doses or quantities, deciding whether or when to escalate, or taking any "
            + "safety-critical action. Examples stay on structuring records from approved source information, checking "
            + "records for missing or inconsistent fields, summarising existing information for the person's review, "
            + "organising handover or meeting preparation, non-specialist administrative wording and training.";

    private static final String VERY_HIGH_ACCOUNTABILITY_RULE = "O1_ACCOUNTABILITY_VERY_HIGH";

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
        highStakesOpportunityIds = List.copyOf(highStakesOpportunityIds);
        safetyCriticalOpportunityIds = List.copyOf(safetyCriticalOpportunityIds);
        bandByMeasure = Map.copyOf(bandByMeasure);
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
                levels(item.levels(), ResponsibilityLevel.HIGH), levels(item.levels(), ResponsibilityLevel.MODERATE),
                item.needsCloseHumanOversight(),
                ResponsibilityExplanations.forRule(item.classificationRule(), item.category()))).toList();

        Map<String, ResponsibilityAnalysisItem> itemsByKey = new LinkedHashMap<>();
        analysis.items().forEach(item -> itemsByKey.put(item.responsibilityKey(), item));
        boolean regulated = analysis.regulatedContextLevel() != ResponsibilityLevel.LOW;
        boolean safetyCriticalSafeguard = plan.safeguards().stream()
                .anyMatch(safeguard -> safeguard.type() == SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK);
        List<Prompt.Opportunity> opportunities = new ArrayList<>();
        Map<String, ResponsibilityCategory> categories = new LinkedHashMap<>();
        List<String> highStakes = new ArrayList<>();
        List<String> safetyCritical = new ArrayList<>();
        for (AiAssistanceOpportunity opportunity : plan.opportunities()) {
            String id = opportunityId(opportunity);
            categories.put(id, opportunity.category());
            ResponsibilityAnalysisItem item = itemsByKey.get(opportunity.responsibilityKey());
            boolean critical = safetyCriticalSafeguard
                    || item.oversightRule().filter(VERY_HIGH_ACCOUNTABILITY_RULE::equals).isPresent();
            boolean stakes = critical || regulated || item.needsCloseHumanOversight()
                    || item.levels().entrySet().stream().anyMatch(level -> level.getKey() != ResponsibilityJudgmentKind.ASSISTANCE_SCOPE
                            && level.getValue() == ResponsibilityLevel.HIGH);
            List<String> limits = new ArrayList<>();
            if (stakes) {
                highStakes.add(id);
                limits.add(HIGH_STAKES_LIMIT);
            }
            if (critical) {
                safetyCritical.add(id);
                limits.add(SAFETY_CRITICAL_LIMIT);
            }
            opportunities.add(new Prompt.Opportunity(id, opportunity.responsibilityKey(), opportunity.responsibilityText(),
                    opportunity.category().label(), levels(item.levels(), ResponsibilityLevel.HIGH),
                    opportunity.guidance(), opportunity.constraints().stream().map(SafeUseConstraint::guidance).toList(),
                    opportunity.needsCloseHumanOversight(), limits));
        }
        List<Prompt.HumanValue> humanValue = new ArrayList<>();
        for (HumanValueArea area : plan.humanValueAreas()) {
            String id = humanValueId(area);
            categories.put(id, area.category());
            humanValue.add(new Prompt.HumanValue(id, area.responsibilityKey(), area.responsibilityText(),
                    area.category().label(), reasonLabel(area.reason().name()), area.explanation(),
                    area.supportingSignals().stream().map(signal -> reasonLabel(signal.name())).toList()));
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
                actions.add(new Prompt.Action(id, action.title(), action.rationale(),
                        action.responsibilityKeys().stream().map(key -> texts.getOrDefault(key, key)).toList(),
                        PILOT_ACTION.equals(action.actionId())));
            }
            String phaseLabel = planPhases.get(index).isEmpty() ? "Phase " + (index + 1)
                    : planPhases.get(index).getFirst().phase().label();
            phases.add(new Prompt.Phase(phaseLabel, actions));
        }
        var prompt = new Prompt(roleReport.profession(),
                truncate(analysis.input().workDescription()), analysis.regulatedContextLevel().label(),
                measures, roleReport.exposureDrivers(),
                roleReport.protectiveFactors(), roleReport.narrative().whatThisMeans().orElse(null), responsibilities,
                opportunities, humanValue, skills,
                plan.safeguards().stream().map(safeguard -> safeguard.guidance()).toList(), phases);
        List<String> storedSkills = plan.skillPriorities().stream().map(SkillPriority::label).toList();
        List<String> unselected = Arrays.stream(SkillFamily.values()).map(SkillFamily::label)
                .filter(label -> !storedSkills.contains(label)).toList();
        return new ProfessionalPaidNarrativeContext(analysis.assessmentId(), analysis.snapshotId(), guidance.guidanceId(),
                roleReport.profession(), prompt, opportunities.stream().map(Prompt.Opportunity::id).toList(),
                humanValue.stream().map(Prompt.HumanValue::id).toList(), skills.stream().map(Prompt.Skill::id).toList(),
                actionIds, categories, storedSkills, unselected, highStakes, safetyCritical,
                measures.stream().collect(java.util.stream.Collectors.toMap(Prompt.Measure::name, Prompt.Measure::band)));
    }

    /** The guidance action whose responsibility the plan trials; its note explains how to run the trial. */
    static final String PILOT_ACTION = "P2_PILOT_WITH_REVIEW";

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

    /** The person's own work description: terms they used themselves (a named system, say) are not invented. */
    public String workDescription() {
        return prompt.workDescription();
    }

    private static List<String> levels(Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels,
            ResponsibilityLevel wanted) {
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
    public record Prompt(String profession, String workDescription, String regulatedContext, List<Measure> measures,
            List<String> factorsRaisingAiExposure, List<String> factorsLoweringAiExposure, String roleLevelNarrative,
            List<Responsibility> responsibilities, List<Opportunity> opportunities, List<HumanValue> humanValueAreas,
            List<Skill> skillPriorities, List<String> safeguards, List<Phase> ninetyDayPlan) {

        public record Measure(String name, String storedScore, String band, String meaning) {
        }

        public record Responsibility(String key, String text, String category, List<String> highSignals,
                List<String> moderateSignals, boolean needsCloseHumanOversight, String interpretation) {
        }

        /** {@code practicalExampleLimits} bound what the practical uses and first experiment may describe. */
        public record Opportunity(String id, String responsibilityKey, String responsibility, String category,
                List<String> highSignals, String howAiMayAssist, List<String> conditions,
                boolean needsCloseHumanOversight, List<String> practicalExampleLimits) {
        }

        public record HumanValue(String id, String responsibilityKey, String responsibility, String category,
                String reason, String reasonExplanation, List<String> alsoReliesOn) {
        }

        public record Skill(String id, String label, String reason, List<String> supportingResponsibilities) {
        }

        public record Phase(String phase, List<Action> actions) {
        }

        /** {@code pilotTrial}: the action that trials AI assistance; its note says how to run the trial. */
        public record Action(String id, String action, String rationale, List<String> responsibilities,
                boolean pilotTrial) {
        }
    }
}
