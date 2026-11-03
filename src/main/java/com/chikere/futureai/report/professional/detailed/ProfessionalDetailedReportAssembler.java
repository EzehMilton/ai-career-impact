package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.PlanNoteRestatement;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeVersions;
import com.chikere.futureai.narrative.professional.paid.StoredProfessionalPaidNarrative;
import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport.*;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.HumanValueReason;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardImportance;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityExplanations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composes {@value ProfessionalDetailedReportVersions#REPORT} from stored artefacts only. Pure: no provider, policy
 * or store is called, and the deterministic order of every guidance list is kept exactly. The paid narrative, when
 * AVAILABLE, adds explanation and practical interpretation beside the deterministic copy, matched by exact id; it
 * never replaces a deterministic item. Stored reports of earlier versions are never passed through here again.
 */
public final class ProfessionalDetailedReportAssembler {

    static final String TITLE = "Professional AI Career Impact Report";
    static final String SUBTITLE = "How AI may affect your work, where it can assist, and where your human contribution "
            + "remains important.";
    private static final String[] NUMBERS = {"none", "one", "two", "three", "four", "five", "six", "seven", "eight"};

    /** The canonical report, with a stored (AVAILABLE or final FALLBACK) paid narrative. */
    public ProfessionalDetailedReport assemble(PurchasedProfessionalReportSnapshot roleSnapshot,
            ProfessionalResponsibilityAnalysisSnapshot analysis, ProfessionalResponsibilityGuidanceSnapshot guidanceSnapshot,
            StoredProfessionalPaidNarrative paidNarrative) {
        Objects.requireNonNull(paidNarrative);
        if (!paidNarrative.guidanceId().equals(guidanceSnapshot.guidanceId())
                || !paidNarrative.analysisId().equals(analysis.snapshotId())) {
            throw new IllegalArgumentException("the paid narrative does not explain this analysis and guidance");
        }
        return assemble(roleSnapshot, analysis, guidanceSnapshot, paidNarrative.status(),
                paidNarrative.status() == PaidNarrativeStatus.AVAILABLE ? paidNarrative.narrative() : Optional.empty(),
                paidNarrative.narrativeVersion());
    }

    /**
     * The provisional report while the paid narrative is retryable: the same deterministic content, never stored, so a
     * later successful preparation can still produce the canonical report.
     */
    public ProfessionalDetailedReport assembleProvisional(PurchasedProfessionalReportSnapshot roleSnapshot,
            ProfessionalResponsibilityAnalysisSnapshot analysis, ProfessionalResponsibilityGuidanceSnapshot guidanceSnapshot) {
        return assemble(roleSnapshot, analysis, guidanceSnapshot, PaidNarrativeStatus.RETRYABLE_FALLBACK, Optional.empty(),
                ProfessionalPaidNarrativeVersions.CURRENT);
    }

    private ProfessionalDetailedReport assemble(PurchasedProfessionalReportSnapshot roleSnapshot,
            ProfessionalResponsibilityAnalysisSnapshot analysis, ProfessionalResponsibilityGuidanceSnapshot guidanceSnapshot,
            PaidNarrativeStatus narrativeStatus, Optional<ProfessionalPaidNarrative> narrative, String narrativeVersion) {
        Objects.requireNonNull(roleSnapshot);
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(guidanceSnapshot);
        if (!roleSnapshot.assessmentId().equals(analysis.assessmentId())
                || !guidanceSnapshot.assessmentId().equals(analysis.assessmentId())
                || !guidanceSnapshot.analysisSnapshotId().equals(analysis.snapshotId())) {
            throw new IllegalArgumentException("the report artefacts do not belong to one assessment and analysis");
        }
        ProfessionalReport role = roleSnapshot.report();
        ProfessionalResponsibilityGuidance guidance = guidanceSnapshot.guidance();
        Map<String, String> texts = new LinkedHashMap<>();
        analysis.items().forEach(item -> texts.put(item.responsibilityKey(), item.responsibilityText()));
        Oversight oversight = Oversight.of(analysis);

        var methodology = role.methodology();
        var provenance = new Provenance(role.reportVersion(), methodology.judgmentSchemaVersion(),
                methodology.decisionPolicyVersion(), methodology.narrativeVersion().orElse(null),
                analysis.schemaVersion(), analysis.policyVersion(), guidanceSnapshot.policyVersion(),
                narrativeVersion);
        String pilot = pilotText(guidance, texts);

        return new ProfessionalDetailedReport(ProfessionalDetailedReportVersions.REPORT, provenance,
                role.developmentFixture(), new Cover(TITLE, role.profession(), role.assessedAt(), SUBTITLE),
                narrativeStatus,
                narrative.map(value -> paragraphs(value.executiveSummary()))
                        .orElseGet(() -> deterministicSummary(role, analysis, guidance, pilot)),
                role.measures().stream().map(measure -> new Measure(measure.measure(),
                        measureName(measure.measure().name()), measure.indicator(), measure.band(),
                        measure.conciseExplanation())).toList(),
                narrative.map(ProfessionalPaidNarrative::scoresTogether)
                        .orElseGet(() -> role.narrative().whatThisMeans().orElse(null)),
                new Factors(role.exposureDrivers(), role.protectiveFactors(),
                        "No strong exposure drivers were identified.",
                        "No strong factors reducing AI Exposure were identified."),
                responsibilities(analysis, narrative, oversight),
                opportunities(guidance, narrative, oversight),
                humanValue(guidance, narrative),
                skills(guidance, narrative, texts),
                safeguards(guidance),
                plan(guidance, narrative),
                narrative.map(ProfessionalPaidNarrative::adaptationStrategy).map(ProfessionalDetailedReportAssembler::strategy)
                        .orElseGet(() -> deterministicStrategy(guidance, texts, pilot)),
                limitations(), nextSteps(pilot),
                narrative.map(ProfessionalPaidNarrative::closingPerspective).orElse(null));
    }

    private static List<String> deterministicSummary(ProfessionalReport role,
            ProfessionalResponsibilityAnalysisSnapshot analysis, ProfessionalResponsibilityGuidance guidance, String pilot) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(role.assessmentSummary().interpretation());
        Map<ResponsibilityCategory, Long> counts = analysis.items().stream().collect(Collectors.groupingBy(
                ResponsibilityAnalysisItem::category, LinkedHashMap::new, Collectors.counting()));
        List<String> parts = new ArrayList<>();
        for (ResponsibilityCategory category : ResponsibilityCategory.values()) {
            long count = counts.getOrDefault(category, 0L);
            if (count > 0) {
                parts.add(capitalised(number(count), parts.isEmpty()) + (count == 1 ? " is " : " are ") + switch (category) {
                    case GOOD_AI_ASSISTANCE_OPPORTUNITY -> count == 1 ? "a good opportunity for AI assistance"
                            : "good opportunities for AI assistance";
                    case AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT -> "work where AI may assist with parts";
                    case HUMAN_LED_AI_SUPPORTING -> "human-led with AI in a supporting role";
                    case MAINLY_HANDS_ON_OR_IN_PERSON -> "mainly hands-on or in person";
                });
            }
        }
        int listed = analysis.items().size();
        paragraphs.add("This report looks at the " + number(listed) + " responsibilit" + (listed == 1 ? "y" : "ies")
                + " you listed, one by one. " + joined(parts) + ".");
        List<String> highlights = new ArrayList<>();
        // The summary names at most the two highest-ranked primary opportunities; every one is listed in its section.
        List<String> primary = guidance.opportunities().stream()
                .filter(opportunity -> opportunity.category() != ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING)
                .limit(2).map(AiAssistanceOpportunity::responsibilityText).toList();
        if (!primary.isEmpty()) {
            highlights.add((primary.size() == 1 ? "The clearest place to try AI assistance, with your review in place, is "
                    : "The clearest places to try AI assistance, with your review in place, are ") + quotedList(primary) + ".");
        }
        if (!guidance.humanValueAreas().isEmpty()) {
            highlights.add("Your own contribution matters most in " + quotedList(guidance.humanValueAreas().stream()
                    .map(HumanValueArea::responsibilityText).toList()) + ".");
        }
        if (!highlights.isEmpty()) {
            paragraphs.add(String.join(" ", highlights));
        }
        paragraphs.add(pilot == null
                ? "The 90-day plan below gives you a simple structure for reviewing your work and your use of AI."
                : "The 90-day plan below turns these findings into a few practical steps, starting with a small, "
                        + "reviewed trial on " + quoted(pilot) + ".");
        return paragraphs;
    }

    private static ResponsibilitySection responsibilities(ProfessionalResponsibilityAnalysisSnapshot analysis,
            Optional<ProfessionalPaidNarrative> narrative, Oversight oversight) {
        List<ResponsibilityCard> cards = analysis.items().stream().map(item -> new ResponsibilityCard(
                item.responsibilityKey(), item.responsibilityText(), item.category(), item.category().label(),
                ResponsibilityExplanations.forRule(item.classificationRule(), item.category()),
                ResponsibilityExplanations.highSignals(item.levels(), item.category()),
                item.needsCloseHumanOversight() && !oversight.sectionLevel())).toList();
        String oversightNote = oversight.all() ? "Every responsibility you listed carries significant accountability, so "
                + "keep close human oversight in place wherever AI assists with any of them."
                : oversight.sectionLevel() ? "Most of the responsibilities you listed carry significant accountability, so "
                        + "keep close human oversight in place wherever AI assists with them." : null;
        return new ResponsibilitySection("Each responsibility you listed was looked at separately. The label "
                + "describes the scope for AI assistance in that responsibility; it is not a score.",
                narrative.map(ProfessionalPaidNarrative::responsibilityPattern).orElse(null), oversightNote, cards);
    }

    private static OpportunitySection opportunities(ProfessionalResponsibilityGuidance guidance,
            Optional<ProfessionalPaidNarrative> narrative, Oversight oversight) {
        Map<String, OpportunityInsight> insights = byId(narrative.map(ProfessionalPaidNarrative::opportunityInsights),
                OpportunityInsight::id);
        Map<SafeguardType, String> listed = new LinkedHashMap<>();
        guidance.safeguards().forEach(safeguard -> listed.put(safeguard.type(), safeguardTitle(safeguard.type())));
        List<Opportunity> items = guidance.opportunities().stream().map(opportunity -> {
            // A condition the Responsible AI section already states is referenced by name, not repeated in full.
            List<String> conditions = new ArrayList<>();
            List<String> applied = new ArrayList<>();
            for (SafeUseConstraint constraint : opportunity.constraints()) {
                String title = listed.get(coveringSafeguard(constraint));
                if (title == null) {
                    conditions.add(constraint.guidance());
                }
                else {
                    applied.add(title);
                }
            }
            if (opportunity.needsCloseHumanOversight() && !oversight.sectionLevel()) {
                conditions.add("This responsibility needs close human oversight.");
            }
            Optional<OpportunityInsight> insight = Optional.ofNullable(
                    insights.get(ProfessionalPaidNarrativeContext.opportunityId(opportunity)));
            return new Opportunity(opportunity.rank(), opportunity.responsibilityKey(), opportunity.responsibilityText(),
                    opportunity.category().label(), whyCandidate(opportunity.category()), opportunity.guidance(),
                    conditions, insight.map(OpportunityInsight::explanation).map(String::strip).orElse(null),
                    insight.map(OpportunityInsight::practicalAiUses).map(ProfessionalDetailedReportAssembler::stripped)
                            .orElse(List.of()),
                    insight.map(OpportunityInsight::humanOwnership).map(ProfessionalDetailedReportAssembler::stripped)
                            .orElse(List.of()),
                    insight.map(OpportunityInsight::firstExperiment).map(String::strip).orElse(null), applied);
        }).toList();
        String introduction = items.isEmpty()
                ? "None of the responsibilities you listed stood out as a clear opportunity for AI assistance. That is a "
                        + "finding in itself: your work relies mainly on things you do in person or decide yourself."
                : "These are the responsibilities where AI may assist most, in order: what assistance could look like, "
                        + "what stays yours, and a small first experiment. The safeguards under Responsible AI use apply "
                        + "to all of them." + (oversight.all() ? " All of them need close human oversight."
                        : oversight.sectionLevel() ? " Most of your work needs close human oversight, as noted above." : "");
        return new OpportunitySection(introduction, items);
    }

    /**
     * How the stored oversight markers are presented, never whether they apply. When at least
     * {@value #SECTION_LEVEL_OVERSIGHT_PERCENT}% of the analysed responsibilities carry the marker, one section-level
     * note replaces the repeated per-card badges and per-opportunity conditions; below that, only the affected
     * responsibilities are marked. The markers themselves stay unchanged in the analysis.
     */
    record Oversight(int marked, int total) {
        static Oversight of(ProfessionalResponsibilityAnalysisSnapshot analysis) {
            return new Oversight((int) analysis.items().stream().filter(ResponsibilityAnalysisItem::needsCloseHumanOversight)
                    .count(), analysis.items().size());
        }

        boolean all() {
            return total > 0 && marked == total;
        }

        boolean sectionLevel() {
            return total > 0 && marked * 100 >= total * SECTION_LEVEL_OVERSIGHT_PERCENT;
        }
    }

    static final int SECTION_LEVEL_OVERSIGHT_PERCENT = 75;

    /** The listed safeguard that states an opportunity condition for the whole report. */
    private static SafeguardType coveringSafeguard(SafeUseConstraint constraint) {
        return switch (constraint) {
            case REVIEW_EVERY_OUTPUT -> SafeguardType.HUMAN_REVIEW_OF_AI_OUTPUT;
            case APPROVED_TOOLS_ONLY -> SafeguardType.APPROVED_TOOLS_AND_CONFIDENTIALITY;
            case KEEP_DECISIONS_HUMAN -> SafeguardType.KEEP_DECISIONS_HUMAN;
        };
    }

    private static String whyCandidate(ResponsibilityCategory category) {
        return switch (category) {
            case GOOD_AI_ASSISTANCE_OPPORTUNITY -> "Much of this responsibility involves information, drafting or "
                    + "checking, and relatively little of it depends on in-person interaction or physical presence.";
            case AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT -> "Parts of this responsibility involve information work where AI "
                    + "may assist, while other parts rely on your judgement.";
            case HUMAN_LED_AI_SUPPORTING -> "This responsibility is human-led; any AI role is limited to organising "
                    + "information beforehand.";
            case MAINLY_HANDS_ON_OR_IN_PERSON -> "This responsibility is mainly hands-on or in person.";
        };
    }

    private static HumanValueSection humanValue(ProfessionalResponsibilityGuidance guidance,
            Optional<ProfessionalPaidNarrative> narrative) {
        Map<String, HumanValueInsight> insights = byId(narrative.map(ProfessionalPaidNarrative::humanValueInsights),
                HumanValueInsight::id);
        Map<String, Long> repeats = guidance.humanValueAreas().stream().collect(Collectors.groupingBy(
                ProfessionalDetailedReportAssembler::humanValueExplanation, Collectors.counting()));
        List<HumanValue> items = guidance.humanValueAreas().stream().map(area -> {
            String explanation = humanValueExplanation(area);
            Optional<HumanValueInsight> insight = Optional.ofNullable(
                    insights.get(ProfessionalPaidNarrativeContext.humanValueId(area)));
            return new HumanValue(area.rank(), area.responsibilityKey(), area.responsibilityText(),
                    reasonLabel(area.reason().name()), repeats.get(explanation) > 1 ? anchored(area, explanation) : explanation,
                    insight.map(HumanValueInsight::explanation).map(String::strip).orElse(null),
                    insight.map(HumanValueInsight::strengthen).map(String::strip).orElse(null));
        }).toList();
        return new HumanValueSection(items.isEmpty()
                ? "No single responsibility stood out as depending mainly on your own interaction, judgement or presence."
                : "These are the responsibilities where your own interaction, judgement, presence or accountability "
                        + "matters most.", items);
    }

    /** The stored reason, plus the area's other stored High signals, so areas sharing a reason still read apart. */
    private static String humanValueExplanation(HumanValueArea area) {
        List<String> others = area.supportingSignals().stream().filter(signal -> signal != area.reason())
                .map(signal -> switch (signal) {
                    case HUMAN_INTERACTION -> "trust and communication with people";
                    case CONTEXTUAL_JUDGEMENT -> "judgement in changing or incomplete situations";
                    case PHYSICAL_PRESENCE -> "being physically present";
                    case ACCOUNTABILITY -> "your accountability for the outcome";
                }).toList();
        return others.isEmpty() ? area.explanation() : area.explanation() + " It also relies on " + joined(others) + ".";
    }

    /**
     * When two areas would read identically, each sentence is tied to its own stored responsibility, so the copy no
     * longer repeats word for word. Only the wording changes: the reason, signals, category and rank stay as stored.
     */
    private static String anchored(HumanValueArea area, String explanation) {
        String text = quoted(area.responsibilityText());
        if (explanation.startsWith("This responsibility ")) {
            return text + " " + explanation.substring("This responsibility ".length());
        }
        String sentence = explanation.replaceFirst(" here\\.", ".");
        return "In " + text + ", " + Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
    }

    private static SkillSection skills(ProfessionalResponsibilityGuidance guidance,
            Optional<ProfessionalPaidNarrative> narrative, Map<String, String> texts) {
        Map<String, SkillInsight> insights = byId(narrative.map(ProfessionalPaidNarrative::skillInsights),
                SkillInsight::id);
        List<Skill> items = guidance.skillPriorities().stream().map(skill -> {
            Optional<SkillInsight> insight = Optional.ofNullable(insights.get(ProfessionalPaidNarrativeContext.skillId(skill)));
            return new Skill(skill.rank(), skill.label(), skill.reason(), skill.supportingResponsibilityKeys().stream()
                    .map(key -> texts.getOrDefault(key, key)).toList(),
                    insight.map(SkillInsight::explanation).map(String::strip).orElse(null),
                    insight.map(SkillInsight::inYourWorkThisMeans).map(ProfessionalDetailedReportAssembler::stripped)
                            .orElse(List.of()));
        }).toList();
        return new SkillSection("These skills follow from the pattern of your responsibilities, in priority order. "
                + "They describe what to strengthen, not a course to take.", null, items);
    }

    private static SafeguardSection safeguards(ProfessionalResponsibilityGuidance guidance) {
        List<Safeguard> items = guidance.safeguards().stream().map(safeguard -> new Safeguard(
                safeguardTitle(safeguard.type()), safeguard.guidance(),
                safeguard.importance() == SafeguardImportance.CRITICAL)).toList();
        return new SafeguardSection("Keep these in place whenever AI assists with any part of your work.", items);
    }

    private static String safeguardTitle(SafeguardType type) {
        return switch (type) {
            case NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK -> "Safety-critical limits";
            case ESCALATE_UNCERTAINTY -> "Escalate uncertainty";
            case APPROVED_TOOLS_AND_CONFIDENTIALITY -> "Approved tools and confidentiality";
            case HUMAN_REVIEW_OF_AI_OUTPUT -> "Human review";
            case KEEP_DECISIONS_HUMAN -> "Keep decisions human";
            case VERIFY_FACTS_FIGURES_AND_SOURCES -> "Verify facts, figures and sources";
        };
    }

    private static PlanSection plan(ProfessionalResponsibilityGuidance guidance,
            Optional<ProfessionalPaidNarrative> narrative) {
        Map<String, String> notes = explanations(narrative, ProfessionalPaidNarrative::planActionNotes);
        var plan = guidance.plan();
        List<List<PlanAction>> phases = List.of(plan.days1To30(), plan.days31To60(), plan.days61To90());
        String[][] headings = {{"Days 1–30", "Observe and prepare"}, {"Days 31–60", "Run controlled experiments"},
                {"Days 61–90", "Standardise what works"}};
        List<Phase> result = new ArrayList<>();
        for (int index = 0; index < phases.size(); index++) {
            int phase = index + 1;
            result.add(new Phase(headings[index][0], headings[index][1], phases.get(index).stream().map(action ->
                    new Action(action.title(), action.rationale(), action.reviewNote(),
                            addedNote(notes.get(ProfessionalPaidNarrativeContext.actionId(phase, action)), action)))
                    .toList()));
        }
        return new PlanSection("Three short phases, each building on the last. Work through the actions in order.",
                narrative.map(ProfessionalPaidNarrative::planIntroduction).orElse(null), result);
    }

    /** The narrative note, unless it only restates the action shown above it: the deterministic text stays. */
    private static String addedNote(String note, PlanAction action) {
        return note == null || PlanNoteRestatement.restates(note, action.title(), action.rationale(), action.reviewNote())
                ? null : note;
    }

    private static List<String> limitations() {
        return List.of(
                "This report describes characteristics of the work you described. It does not assess you as a person.",
                "It is not a prediction of job loss, and it does not forecast your career.",
                "AI capabilities, and the way workplaces adopt them, keep changing.",
                "The actual effect on your work depends on your organisation, the tools it approves, regulation and "
                        + "how changes are introduced.",
                "Use these results to support your own judgement, not to replace it.");
    }

    private static List<String> nextSteps(String pilot) {
        return List.of(pilot == null ? "Use the 90-day plan as a structure for reviewing your work."
                        : "Use the 90-day plan, starting with the small, reviewed trial on " + quoted(pilot) + ".",
                "Use the Responsible AI use section as your checklist whenever AI assists with your work.",
                "Look back at what you have learned at the end of the 90 days.",
                "Revisit the assessment if your role or responsibilities change materially.");
    }

    /** The responsibility the plan pilots, if any: the first responsibility of the pilot action. */
    private static String pilotText(ProfessionalResponsibilityGuidance guidance, Map<String, String> texts) {
        return guidance.plan().days31To60().stream()
                .filter(action -> action.actionId().equals("P2_PILOT_WITH_REVIEW"))
                .flatMap(action -> action.responsibilityKeys().stream()).findFirst()
                .map(texts::get).orElse(null);
    }

    private static Map<String, String> explanations(Optional<ProfessionalPaidNarrative> narrative,
            Function<ProfessionalPaidNarrative, List<ItemExplanation>> field) {
        Map<String, String> byId = new LinkedHashMap<>();
        narrative.map(field).orElse(List.of()).forEach(item -> byId.put(item.id().strip(), item.explanation().strip()));
        return byId;
    }

    /** Exact-id lookup; the validator has already held the ids to the deterministic order. */
    private static <T> Map<String, T> byId(Optional<List<T>> items, Function<T, String> id) {
        Map<String, T> byId = new LinkedHashMap<>();
        items.orElse(List.of()).forEach(item -> byId.put(id.apply(item).strip(), item));
        return byId;
    }

    private static List<String> stripped(List<String> values) {
        return values.stream().map(String::strip).toList();
    }

    private static AdaptationStrategy strategy(ProfessionalPaidNarrative.AdaptationStrategy strategy) {
        return new AdaptationStrategy(stripped(strategy.protect()), stripped(strategy.learn()),
                stripped(strategy.experiment()), stripped(strategy.strengthen()), stripped(strategy.monitor()), true);
    }

    /**
     * The adaptation strategy without the paid narrative, composed only from stored guidance: the top human-value
     * areas to protect, the AI-use skills to learn, the plan's pilot to experiment with, the human skills to
     * strengthen and the ranked opportunities to keep watching. Never a prediction.
     */
    static AdaptationStrategy deterministicStrategy(ProfessionalResponsibilityGuidance guidance, Map<String, String> texts,
            String pilot) {
        List<String> protect = guidance.humanValueAreas().stream().limit(2).map(area -> "Your " + humanContribution(area.reason())
                + " in " + quoted(area.responsibilityText()) + ".").toList();
        List<SkillPriority> aiSkills = guidance.skillPriorities().stream().filter(skill -> !humanSkill(skill.family()))
                .limit(2).toList();
        List<String> learn = aiSkills.stream().map(skill -> skill.label() + (skill.supportingResponsibilityKeys().isEmpty()
                ? "." : ", starting with " + quoted(texts.getOrDefault(skill.supportingResponsibilityKeys().getFirst(),
                skill.supportingResponsibilityKeys().getFirst())) + ".")).toList();
        List<String> strengthen = guidance.skillPriorities().stream().filter(skill -> humanSkill(skill.family())).limit(2)
                .map(skill -> skill.label() + (skill.supportingResponsibilityKeys().isEmpty() ? "."
                        : ", especially in " + quoted(texts.getOrDefault(skill.supportingResponsibilityKeys().getFirst(),
                        skill.supportingResponsibilityKeys().getFirst())) + ".")).toList();
        List<String> monitor = guidance.opportunities().stream().limit(2).map(opportunity -> "How AI assistance "
                + "available through your organisation's approved tools fits the preparation and checking in "
                + quoted(opportunity.responsibilityText()) + ".").toList();
        return new AdaptationStrategy(
                protect.isEmpty() ? List.of("Your own review and judgement in every responsibility AI assists with.") : protect,
                learn.isEmpty() ? List.of("Responsible and approved use of AI, through your organisation's policy.") : learn,
                List.of(pilot == null ? "Look for routine information work, such as notes or summaries, before trying "
                        + "any AI assistance." : "A small, reviewed trial on " + quoted(pilot) + ", as set out in your "
                        + "90-day plan."),
                strengthen.isEmpty() ? guidance.humanValueAreas().stream().limit(1).map(area -> "Your "
                        + humanContribution(area.reason()) + ", as AI assistance around it grows.").toList() : strengthen,
                monitor.isEmpty() ? List.of("Which routine information tasks in your work AI assistance could support "
                        + "under your review.") : monitor,
                false);
    }

    private static boolean humanSkill(SkillFamily family) {
        return family == SkillFamily.JUDGEMENT_AND_DECISION_MAKING || family == SkillFamily.COMMUNICATION_AND_RELATIONSHIPS
                || family == SkillFamily.HANDS_ON_PRACTICE_AND_SAFETY;
    }

    private static String humanContribution(HumanValueReason reason) {
        return switch (reason) {
            case HUMAN_INTERACTION -> "direct communication and the trust people place in you";
            case CONTEXTUAL_JUDGEMENT -> "judgement in changing or incomplete situations";
            case PHYSICAL_PRESENCE -> "hands-on, in-person expertise";
            case ACCOUNTABILITY -> "ownership of outcomes and their review";
        };
    }

    private static List<String> paragraphs(String text) {
        return java.util.Arrays.stream(text.strip().split("\\n\\s*\\n")).map(String::strip)
                .filter(value -> !value.isEmpty()).toList();
    }

    static String measureName(String name) {
        return switch (name) {
            case "AI_EXPOSURE" -> "AI Exposure";
            case "HUMAN_ADVANTAGE" -> "Human Advantage";
            case "AI_COMPLEMENTARITY" -> "AI Complementarity";
            case "ROLE_ADAPTABILITY" -> "Role Adaptability";
            default -> name;
        };
    }

    private static String reasonLabel(String reason) {
        return switch (reason) {
            case "HUMAN_INTERACTION" -> "Human interaction";
            case "CONTEXTUAL_JUDGEMENT" -> "Contextual judgement";
            case "PHYSICAL_PRESENCE" -> "Physical or in-person work";
            case "ACCOUNTABILITY" -> "Accountability";
            default -> reason;
        };
    }

    private static String number(long count) {
        return count < NUMBERS.length ? NUMBERS[(int) count] : Long.toString(count);
    }

    private static String capitalised(String word, boolean first) {
        return first ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
    }

    private static String joined(List<String> parts) {
        if (parts.size() <= 1) {
            return String.join("", parts);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    private static String quotedList(List<String> texts) {
        return joined(texts.stream().map(ProfessionalDetailedReportAssembler::quoted).toList());
    }

    private static String quoted(String text) {
        return "“" + text + "”";
    }
}
