package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceAction.Horizon;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceItem.Section;
import com.chikere.futureai.coursemodule.university.guidance.UniversitySkillPriority.SkillTheme;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.AI_ASSISTED_LEARNING;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.HUMAN_JUDGEMENT_LED;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.TECHNICAL_CORE;

/**
 * Deterministic {@code university-guidance-policy-v2}: practical guidance from one immutable module-analysis
 * snapshot. It is {@link UniversityGuidancePolicyV1} with one change: action M4 ("collect examples of your
 * judgement") is added only when a judgement-led module or a module with HIGH interpersonal work exists to name. v1
 * threw when critical evaluation was selected only through an independent-demonstration module (for example a
 * final-year project) and neither kind of module existed; v2 omits M4 instead and builds the rest of the plan exactly
 * as v1 does. Every other rule, template, threshold, cap and ordering is v1's, and v1 stays frozen for the guidance
 * already produced under it.
 *
 * <p>The v1 description follows.</p>
 *
 * <p>Practical guidance from one immutable module-analysis snapshot. It reads only the snapshot's frozen judgments and classifications, never a module or course title as a
 * rule, and never the snapshot's provider metadata. Titles appear only as data inside fixed wording.
 *
 * <p>No guidance predicts employment, salary or the future of an occupation, says AI cannot do something, or says
 * what AI use an institution permits. AI learning assistance describes where AI may support learning or
 * preparation; it does not determine what use is permitted in assessed work.</p>
 *
 * <p>Sections are capped and ranked by evidence, with course order as the last tie-break. Modules are spread across
 * kinds of reason before any kind is repeated, so one pattern cannot fill a section. Skill themes merge overlapping
 * advice (critical evaluation and independent reasoning are one theme), and each action cites the evidence that
 * selected it.</p>
 *
 * <p>Frozen in Stage 5F against the seven frozen live courses. Any change to a rule, template, threshold, cap or
 * ordering needs a new policy version, so guidance already produced under v1 keeps its meaning. The same holds for
 * v2.</p>
 */
public final class UniversityGuidancePolicyV2 {

    public static final String POLICY_VERSION = "university-guidance-policy-v2";
    public static final String SUPPORTED_MODULE_POLICY_VERSION = UniversityModulePolicyV1.POLICY_VERSION;

    public static final int MAXIMUM_AI_OPPORTUNITIES = 4;
    public static final int MAXIMUM_OWN_CAPABILITY = 4;
    public static final int MAXIMUM_TECHNICAL_FOUNDATIONS = 3;
    /** In a section, at most this many items for one kind of reason before any kind is repeated. */
    public static final int FIRST_PASS_PER_REASON = 2;
    public static final int MINIMUM_SKILLS = 3;
    public static final int MAXIMUM_SKILLS = 5;
    /** A skill theme needs this many supporting modules, unless it is relaxed in to reach the minimum. */
    public static final int THEME_MINIMUM_MODULES = 2;
    public static final int MINIMUM_ACTIONS = 2;
    public static final int MAXIMUM_ACTIONS = 4;
    /** A non-core module with technical depth at least this still counts as a technical foundation. */
    public static final double TECHNICAL_FOUNDATION_FROM = 3.0;
    /** At most this many module titles are named in one sentence; the rest are counted. */
    public static final int NAMED_MODULES = 3;

    private static final double HIGH = UniversityModulePolicyV1.HIGH_FROM;
    private static final double TOLERANCE = 1.0e-9;

    public UniversityGuidance decide(UniversityModuleAnalysisSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (!SUPPORTED_MODULE_POLICY_VERSION.equals(snapshot.policyVersion())) {
            throw new IllegalArgumentException("guidance v2 reads " + SUPPORTED_MODULE_POLICY_VERSION
                    + " classifications, not " + snapshot.policyVersion());
        }
        List<Module> modules = snapshot.items().stream().map(Module::of).toList();

        List<UniversityGuidanceItem> opportunities = aiLearningOpportunities(modules);
        List<UniversityGuidanceItem> ownCapability = ownCapability(modules);
        List<UniversityGuidanceItem> technical = technicalFoundations(modules);
        List<UniversityGuidanceItem> safeguards = safeguards(modules);
        List<UniversitySkillPriority> skills = skillPriorities(modules);
        Map<SkillTheme, Integer> rank = new EnumMap<>(SkillTheme.class);
        skills.forEach(skill -> rank.put(skill.theme(), skill.rank()));
        var evidence = new Evidence(modules, opportunities, ownCapability, technical, safeguards, rank);

        return new UniversityGuidance(POLICY_VERSION, snapshot.assessmentId(), snapshot.snapshotId(),
                snapshot.policyVersion(), opportunities, ownCapability, technical, safeguards, skills,
                actions(Horizon.NEXT_30_DAYS, next30Days(evidence), rank),
                actions(Horizon.THIS_TERM, thisTerm(evidence), rank),
                actions(Horizon.NEXT_6_TO_12_MONTHS, next6To12Months(evidence), rank));
    }

    // ---- A. AI learning opportunities ----

    /**
     * Modules with HIGH AI learning assistance that are not mainly practical. Ranked by what AI can usefully support
     * (AI-assisted learning, then technical core, then judgement-led, then independent-demonstration work), then
     * modules without safeguard flags, then AI assistance, then course order.
     */
    private static List<UniversityGuidanceItem> aiLearningOpportunities(List<Module> modules) {
        List<Module> ranked = modules.stream()
                .filter(module -> high(module.ai()) && module.category() != PRACTICAL_OR_EXPERIENTIAL)
                .sorted(Comparator.comparingInt((Module module) -> aiRank(module.category()))
                        .thenComparing(Module::integrity).thenComparing(Module::professional)
                        .thenComparing(Comparator.comparingDouble(Module::ai).reversed())
                        .thenComparingInt(Module::id))
                .toList();
        return spread(ranked, Module::category, MAXIMUM_AI_OPPORTUNITIES).stream().map(module -> {
            String text = switch (module.category()) {
                case AI_ASSISTED_LEARNING -> "AI tools could help you get more from " + module.title()
                        + ": explaining ideas, comparing perspectives and revising key material.";
                case TECHNICAL_CORE -> "AI tools could support your learning in " + module.title()
                        + " by explaining concepts, generating practice problems and helping you check your own "
                        + "working.";
                case HUMAN_JUDGEMENT_LED -> "AI tools could help you prepare for " + module.title()
                        + " by summarising reading, exploring different interpretations and testing your arguments, "
                        + "while the judgement stays yours.";
                case AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION -> "AI tools could help you brainstorm, "
                        + "research and plan in " + module.title() + ", but the finished work needs to show your own "
                        + "capability.";
                default -> "AI tools could support preparation and revision in " + module.title() + ".";
            };
            return item("ai-opportunity-" + module.id(), Section.AI_LEARNING_OPPORTUNITY,
                    "GA_" + module.category().name(), module.title(), text, List.of(module));
        }).toList();
    }

    private static int aiRank(UniversityModuleCategory category) {
        return switch (category) {
            case AI_ASSISTED_LEARNING -> 0;
            case TECHNICAL_CORE -> 1;
            case HUMAN_JUDGEMENT_LED -> 2;
            case AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION -> 3;
            case MIXED -> 4;
            case PRACTICAL_OR_EXPERIENTIAL -> 5;
        };
    }

    // ---- B. Where your own capability matters most ----

    private enum OwnReason { PRACTICAL_WITH_PEOPLE, PRACTICAL, PEOPLE, JUDGEMENT, DEMONSTRATION }

    /**
     * Judgement-led, practical, independent-demonstration or integrity-sensitive modules. Ranked by the number of
     * supporting signals (a human or practical category, the integrity flag, the professional flag, HIGH interpersonal
     * work), then the strength of the deciding characteristic, then course order.
     */
    private static List<UniversityGuidanceItem> ownCapability(List<Module> modules) {
        List<Module> ranked = modules.stream()
                .filter(module -> module.category() == HUMAN_JUDGEMENT_LED || module.category() == PRACTICAL_OR_EXPERIENTIAL
                        || module.category() == AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION || module.integrity())
                .sorted(Comparator.comparingInt(UniversityGuidancePolicyV2::ownSignals).reversed()
                        .thenComparing(Comparator.comparingDouble(UniversityGuidancePolicyV2::ownStrength).reversed())
                        .thenComparingInt(Module::id))
                .toList();
        return spread(ranked, UniversityGuidancePolicyV2::ownReason, MAXIMUM_OWN_CAPABILITY).stream().map(module -> {
            OwnReason reason = ownReason(module);
            String text = switch (reason) {
                case PRACTICAL_WITH_PEOPLE -> module.title() + " places greater weight on practical and interpersonal "
                        + "capability. Your own skill with people, built through real practice, is especially important "
                        + "here.";
                case PRACTICAL -> module.title() + " places greater weight on practical or hands-on capability. Your "
                        + "own skill, built by doing the work, is especially important here.";
                case PEOPLE -> module.title() + " places greater weight on interpersonal capability. Your own "
                        + "communication and judgement with other people are especially important here.";
                case JUDGEMENT -> "Your own judgement is especially important in " + module.title()
                        + ": interpreting, framing problems and reaching conclusions you can explain and defend.";
                case DEMONSTRATION -> module.title() + " strongly depends on you demonstrating your own capability. "
                        + "Treat it as a place to build, and show, your own work.";
            };
            return item("own-capability-" + module.id(), Section.OWN_CAPABILITY, "GB_" + reason.name(), module.title(),
                    text, List.of(module));
        }).toList();
    }

    private static OwnReason ownReason(Module module) {
        if (module.category() == PRACTICAL_OR_EXPERIENTIAL) return high(module.interpersonal())
                ? OwnReason.PRACTICAL_WITH_PEOPLE : OwnReason.PRACTICAL;
        if (module.category() == HUMAN_JUDGEMENT_LED) return high(module.interpersonal()) ? OwnReason.PEOPLE
                : OwnReason.JUDGEMENT;
        return OwnReason.DEMONSTRATION;
    }

    private static int ownSignals(Module module) {
        int signals = module.category() == HUMAN_JUDGEMENT_LED || module.category() == PRACTICAL_OR_EXPERIENTIAL ? 1 : 0;
        signals += module.integrity() ? 1 : 0;
        signals += module.professional() ? 1 : 0;
        signals += high(module.interpersonal()) ? 1 : 0;
        return signals;
    }

    private static double ownStrength(Module module) {
        return switch (ownReason(module)) {
            case PRACTICAL, PRACTICAL_WITH_PEOPLE -> module.practical();
            case PEOPLE -> module.interpersonal();
            case JUDGEMENT -> module.judgement();
            case DEMONSTRATION -> module.independent();
        };
    }

    // ---- C. Technical and analytical foundations ----

    /** Technical-core modules, then other modules with technical depth of at least 3.0; by depth, then course order. */
    private static List<UniversityGuidanceItem> technicalFoundations(List<Module> modules) {
        return modules.stream()
                .filter(module -> module.category() == TECHNICAL_CORE
                        || module.technical() >= TECHNICAL_FOUNDATION_FROM - TOLERANCE)
                .sorted(Comparator.comparing((Module module) -> module.category() != TECHNICAL_CORE)
                        .thenComparing(Comparator.comparingDouble(Module::technical).reversed())
                        .thenComparingInt(Module::id))
                .limit(MAXIMUM_TECHNICAL_FOUNDATIONS)
                .map(module -> item("technical-foundation-" + module.id(), Section.TECHNICAL_FOUNDATION,
                        module.category() == TECHNICAL_CORE ? "GC_TECHNICAL_CORE" : "GC_TECHNICAL_DEPTH", module.title(),
                        "Strengthening your foundations in " + module.title() + " makes you more effective with AI "
                                + "tools: you are better placed to judge whether AI-generated calculations, code or "
                                + "explanations are correct.", List.of(module)))
                .toList();
    }

    // ---- D. Safeguards, aggregated: at most one of each kind ----

    private static List<UniversityGuidanceItem> safeguards(List<Module> modules) {
        List<UniversityGuidanceItem> safeguards = new ArrayList<>();
        List<Module> integrity = modules.stream().filter(Module::integrity).toList();
        if (!integrity.isEmpty()) {
            safeguards.add(item("safeguard-independent-demonstration", Section.SAFEGUARD,
                    "SG1_INDEPENDENT_DEMONSTRATION", "Your own work in assessed modules", "In " + names(integrity)
                            + ", AI may be useful for learning and preparation, but " + (integrity.size() == 1
                            ? "the module strongly depends" : "these modules strongly depend") + " on demonstrating "
                            + "your own capability. Check your institution's assessment rules before using AI in "
                            + "assessed work.", integrity));
        }
        List<Module> professional = modules.stream().filter(Module::professional).toList();
        if (!professional.isEmpty()) {
            safeguards.add(item("safeguard-professional-standards", Section.SAFEGUARD, "SG2_PROFESSIONAL_STANDARDS",
                    "Professional and client-facing standards", names(professional) + (professional.size() == 1
                            ? " appears" : " appear") + " to involve professional, clinical or client-facing "
                            + "standards. Treat AI output as support rather than authority, and verify important "
                            + "conclusions against appropriate course or professional guidance.", professional));
        }
        List<Module> technical = modules.stream().filter(module -> module.category() == TECHNICAL_CORE
                && high(module.ai())).toList();
        if (!technical.isEmpty()) {
            safeguards.add(item("safeguard-verify-technical-output", Section.SAFEGUARD, "SG3_VERIFY_TECHNICAL_OUTPUT",
                    "Checking AI-generated technical work", "When AI helps with calculations, code or technical "
                            + "reasoning in " + names(technical) + ", check its working yourself: AI output can look "
                            + "correct and still be wrong.", technical));
        }
        List<Module> judgement = modules.stream().filter(module -> module.category() == HUMAN_JUDGEMENT_LED
                && high(module.ai())).toList();
        if (judgement.size() >= THEME_MINIMUM_MODULES) {
            safeguards.add(item("safeguard-verify-sources", Section.SAFEGUARD, "SG4_VERIFY_SOURCES",
                    "Checking AI-generated claims and sources", "When AI summarises reading or suggests claims, "
                            + "quotations or references for " + names(judgement) + ", check them against the original "
                            + "sources before relying on them.", judgement));
        }
        return safeguards;
    }

    // ---- Skill priorities ----

    /** @param strength the count of supporting modules that ranks the theme */
    private record ThemeEvidence(SkillTheme theme, String ruleId, List<Module> modules, int strength, boolean qualifies,
            boolean relaxable) {
    }

    private static List<UniversitySkillPriority> skillPriorities(List<Module> modules) {
        int n = modules.size();
        List<ThemeEvidence> themes = new ArrayList<>();
        // Judgement-led modules, and modules where no characteristic dominated but judgement is HIGH (dissertations,
        // capstones). Practical and technical modules are excluded even when their judgement value is high.
        themes.add(counted(SkillTheme.CRITICAL_EVALUATION, "SK_CRITICAL_EVALUATION", modules, module ->
                module.category() == HUMAN_JUDGEMENT_LED
                        || module.category() == AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION && high(module.judgement())));
        themes.add(counted(SkillTheme.PRACTICAL_APPLICATION, "SK_PRACTICAL_APPLICATION", modules,
                module -> module.category() == PRACTICAL_OR_EXPERIENTIAL));
        themes.add(counted(SkillTheme.TECHNICAL_FOUNDATIONS, "SK_TECHNICAL_FOUNDATIONS", modules,
                module -> module.category() == TECHNICAL_CORE));
        themes.add(counted(SkillTheme.COMMUNICATION_AND_COLLABORATION, "SK_COMMUNICATION_AND_COLLABORATION", modules,
                module -> high(module.interpersonal())));
        themes.add(counted(SkillTheme.PROFESSIONAL_JUDGEMENT, "SK_PROFESSIONAL_JUDGEMENT", modules, Module::professional));
        themes.add(counted(SkillTheme.INDEPENDENT_WORK, "SK_INDEPENDENT_WORK", modules, Module::integrity));
        // AI literacy is not made universal by generally high AI assistance. It needs HIGH assistance in at least half
        // the modules and at least one module where using AI needs care (integrity-sensitive, technical, or AI-defined).
        // Its strength counts only the AI-defined modules, so it never outranks a theme on evidence that theme owns.
        long aiHigh = modules.stream().filter(module -> high(module.ai())).count();
        List<Module> aiCare = modules.stream().filter(module -> high(module.ai()) && (module.integrity()
                || module.category() == TECHNICAL_CORE || aiDefined(module))).toList();
        int aiStrength = (int) modules.stream().filter(module -> high(module.ai()) && aiDefined(module)).count();
        themes.add(new ThemeEvidence(SkillTheme.AI_LITERACY, "SK_AI_LITERACY", aiCare, aiStrength,
                2 * aiHigh >= n && !aiCare.isEmpty(), aiHigh > 0 && !aiCare.isEmpty()));

        Comparator<ThemeEvidence> strongest = Comparator.comparingInt(ThemeEvidence::strength).reversed()
                .thenComparingInt(theme -> theme.theme().ordinal());
        List<ThemeEvidence> chosen = new ArrayList<>(themes.stream().filter(ThemeEvidence::qualifies).sorted(strongest)
                .limit(MAXIMUM_SKILLS).toList());
        boolean relaxedAny = false;
        if (chosen.size() < MINIMUM_SKILLS) {
            for (ThemeEvidence theme : themes.stream().filter(theme -> !theme.qualifies() && theme.relaxable())
                    .sorted(strongest).toList()) {
                if (chosen.size() >= MINIMUM_SKILLS) break;
                chosen.add(theme);
                relaxedAny = true;
            }
        }
        List<UniversitySkillPriority> priorities = new ArrayList<>();
        for (int index = 0; index < chosen.size(); index++) {
            ThemeEvidence theme = chosen.get(index);
            priorities.add(new UniversitySkillPriority(theme.theme(), theme.ruleId(), theme.theme().title(),
                    skillExplanation(theme.theme(), theme.modules(), n), ids(theme.modules()),
                    (double) theme.strength() / n, index + 1, relaxedAny && !theme.qualifies()));
        }
        return List.copyOf(priorities);
    }

    private static boolean aiDefined(Module module) {
        return module.category() == AI_ASSISTED_LEARNING
                || module.category() == AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION;
    }

    private static ThemeEvidence counted(SkillTheme theme, String ruleId, List<Module> modules, Predicate<Module> supports) {
        List<Module> supporting = modules.stream().filter(supports).toList();
        return new ThemeEvidence(theme, ruleId, supporting, supporting.size(), supporting.size() >= THEME_MINIMUM_MODULES,
                !supporting.isEmpty());
    }

    private static String skillExplanation(SkillTheme theme, List<Module> supporting, int n) {
        boolean one = supporting.size() == 1;
        String share = supporting.size() + " of your " + n + " analysed modules";
        String examples = (one ? "" : ", such as ") + names(supporting.subList(0, Math.min(2, supporting.size())));
        String subject = one ? names(supporting) : share;
        return switch (theme) {
            case CRITICAL_EVALUATION -> subject + (one ? " centres" : " centre") + " on interpretation and judgement"
                    + (one ? "" : examples) + ". Building your own reasoning, and testing AI output against it, is a "
                    + "priority.";
            case PRACTICAL_APPLICATION -> subject + (one ? " places" : " place") + " weight on practical or hands-on "
                    + "work" + (one ? "" : examples) + ". Developing that capability through practice and feedback is a "
                    + "priority.";
            case TECHNICAL_FOUNDATIONS -> subject + (one ? " has" : " have") + " a strong technical or quantitative core"
                    + (one ? "" : examples) + ". Secure foundations let you use AI tools well and check their technical "
                    + "output.";
            case COMMUNICATION_AND_COLLABORATION -> subject + (one ? " involves" : " involve") + " substantial work with "
                    + "other people" + (one ? "" : examples) + ". Communication and collaboration are a priority.";
            case PROFESSIONAL_JUDGEMENT -> subject + (one ? " appears" : " appear") + " shaped by professional or "
                    + "client-facing standards" + (one ? "" : examples) + ". Applying those standards with your own "
                    + "judgement is a priority.";
            case INDEPENDENT_WORK -> subject + (one ? " strongly depends" : " strongly depend") + " on demonstrating "
                    + "your own capability" + (one ? "" : examples) + ". Building, and keeping evidence of, your own work "
                    + "is a priority.";
            case AI_LITERACY -> "AI tools could support learning in most of your modules. In " + names(supporting)
                    + ", using them well needs particular care: knowing where they help, checking what they produce "
                    + "and following your institution's rules.";
        };
    }

    // ---- Action plan ----

    private record Evidence(List<Module> modules, List<UniversityGuidanceItem> opportunities,
            List<UniversityGuidanceItem> ownCapability, List<UniversityGuidanceItem> technical,
            List<UniversityGuidanceItem> safeguards, Map<SkillTheme, Integer> rank) {

        boolean selected(SkillTheme theme) {
            return rank.containsKey(theme);
        }

        int rankOf(SkillTheme theme) {
            return rank.getOrDefault(theme, Integer.MAX_VALUE);
        }

        List<Module> where(Predicate<Module> filter) {
            return modules.stream().filter(filter).toList();
        }

        Optional<UniversityGuidanceItem> safeguard(String ruleId) {
            return safeguards.stream().filter(item -> item.ruleId().equals(ruleId)).findFirst();
        }

        Module module(int id) {
            return modules.stream().filter(module -> module.id() == id).findFirst().orElseThrow();
        }
    }

    /** An eligible action: its fixed position breaks ties after the rank of the themes it serves. */
    private record Candidate(String ruleId, int position, String text, List<Integer> moduleIds, List<SkillTheme> themes,
            boolean fallback) {
    }

    /*
     * Every action earns its place from course-specific evidence. Each rule has a strict gate; the older, looser gate is
     * kept only as a fallback, used when a horizon would otherwise have fewer than two actions, and marked as such.
     * AI-generic actions never qualify on high AI learning assistance alone, which is true almost everywhere.
     */

    private static List<Candidate> next30Days(Evidence evidence) {
        List<Candidate> candidates = new ArrayList<>();
        List<Module> integrity = evidence.where(Module::integrity);
        if (!integrity.isEmpty()) {
            candidates.add(new Candidate("D1_CHECK_ASSESSMENT_RULES", 1, "Check your institution's and each module's "
                    + "rules on using AI in assessed work for " + names(integrity) + ", and note where they differ.",
                    ids(integrity), List.of(SkillTheme.INDEPENDENT_WORK, SkillTheme.AI_LITERACY),
                    !integrityMatters(evidence)));
        }
        if (!evidence.opportunities().isEmpty()) {
            UniversityGuidanceItem first = evidence.opportunities().getFirst();
            String title = first.title();
            UniversityModuleCategory category = first.categories().getFirst();
            String[] family = switch (category) {
                case TECHNICAL_CORE -> new String[] {"D2T_USE_AI_TO_EXPLAIN_AND_PRACTISE", "Ask AI to explain a concept "
                        + "from " + title + " in a different way and to set you practice problems, then solve them "
                        + "yourself and check the method."};
                case HUMAN_JUDGEMENT_LED -> new String[] {"D2J_USE_AI_TO_TEST_INTERPRETATIONS", "Ask AI for alternative "
                        + "interpretations of, or questions about, one reading in " + title + ", then decide which hold "
                        + "up and why."};
                case AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION -> new String[] {
                        "D2I_USE_AI_TO_PLAN_NOT_TO_PRODUCE", "Use AI to help you plan and structure your preparation for "
                        + title + ", keeping the content and the decisions your own."};
                default -> new String[] {"D2L_USE_AI_TO_REVISE", "Ask AI to explain the key ideas from " + title
                        + " and to quiz you on them, then check the answers against your notes."};
            };
            candidates.add(new Candidate(family[0], 2, family[1], first.moduleIds(), List.of(SkillTheme.AI_LITERACY),
                    !(evidence.selected(SkillTheme.AI_LITERACY) && aiPattern(evidence) >= 1)));
        }
        Optional<UniversityGuidanceItem> technical = evidence.safeguard("SG3_VERIFY_TECHNICAL_OUTPUT");
        Optional<UniversityGuidanceItem> sources = evidence.safeguard("SG4_VERIFY_SOURCES");
        boolean technicalFirst = technical.isPresent() && (sources.isEmpty() || evidence.rankOf(
                SkillTheme.TECHNICAL_FOUNDATIONS) <= evidence.rankOf(SkillTheme.CRITICAL_EVALUATION));
        if (technicalFirst) {
            Module module = evidence.module(technical.get().moduleIds().getFirst());
            candidates.add(new Candidate("D3T_CHECK_AI_TECHNICAL_WORKING", 3, "Take one AI-generated calculation, "
                    + "piece of code or technical explanation for " + module.title() + " and check it step by step "
                    + "against your own working.", List.of(module.id()), List.of(SkillTheme.TECHNICAL_FOUNDATIONS),
                    !evidence.selected(SkillTheme.TECHNICAL_FOUNDATIONS)));
        }
        else {
            // Without the aggregated source safeguard, a single judgement-led module can still support this as a fallback.
            Optional<Module> module = sources.map(item -> evidence.module(item.moduleIds().getFirst()))
                    .or(() -> evidence.where(candidate -> candidate.category() == HUMAN_JUDGEMENT_LED && high(candidate.ai()))
                            .stream().findFirst());
            module.ifPresent(found -> candidates.add(new Candidate("D3S_CHECK_AI_CLAIMS_AGAINST_SOURCES", 3, "Take one "
                    + "AI-generated summary or claim about your reading for " + found.title() + " and check it against "
                    + "the original source, including any quotations or references.", List.of(found.id()),
                    List.of(SkillTheme.CRITICAL_EVALUATION), sources.isEmpty()
                            || !evidence.selected(SkillTheme.CRITICAL_EVALUATION))));
        }
        if (evidence.selected(SkillTheme.TECHNICAL_FOUNDATIONS) && !evidence.technical().isEmpty()) {
            UniversityGuidanceItem first = evidence.technical().getFirst();
            candidates.add(new Candidate("D4_STRENGTHEN_ONE_FOUNDATION", 4, "Pick the topic in " + first.title()
                    + " you find hardest and work on it without AI first, so you can judge AI explanations of it later.",
                    first.moduleIds(), List.of(SkillTheme.TECHNICAL_FOUNDATIONS), false));
        }
        if (evidence.selected(SkillTheme.PROFESSIONAL_JUDGEMENT)) {
            Module first = evidence.where(Module::professional).getFirst();
            candidates.add(new Candidate("D5_FIND_THE_STANDARDS_THAT_APPLY", 5, "Find the course or professional "
                    + "guidance that applies to " + first.title() + ", and keep it to hand as the reference you check "
                    + "AI output against.", List.of(first.id()), List.of(SkillTheme.PROFESSIONAL_JUDGEMENT), false));
        }
        firstOwn(evidence, "GB_PRACTICAL_WITH_PEOPLE").or(() -> firstOwn(evidence, "GB_PRACTICAL"))
                .filter(item -> evidence.selected(SkillTheme.PRACTICAL_APPLICATION))
                .ifPresent(item -> candidates.add(new Candidate("D6_AGREE_A_PRACTICAL_FOCUS", 6, "Before your next "
                        + "practical session in " + item.title() + ", agree one skill to work on and how you will get "
                        + "feedback on it.", item.moduleIds(), List.of(SkillTheme.PRACTICAL_APPLICATION), false)));
        candidates.add(new Candidate("D7_MAP_WHERE_AI_HELPS", 7, "List which of your modules AI could help you revise or "
                + "research, and which depend more on your own work.", List.of(), List.of(SkillTheme.AI_LITERACY), true));
        return candidates;
    }

    private static List<Candidate> thisTerm(Evidence evidence) {
        List<Candidate> candidates = new ArrayList<>();
        List<Module> integrity = evidence.where(Module::integrity);
        if (!integrity.isEmpty()) {
            candidates.add(new Candidate("T1_KEEP_EVIDENCE_OF_YOUR_OWN_WORK", 1, "In " + names(integrity) + ", keep "
                    + "notes, drafts or design records that show how your own work developed.", ids(integrity),
                    List.of(SkillTheme.INDEPENDENT_WORK), !integrityMatters(evidence)));
        }
        firstOwn(evidence, "GB_PRACTICAL").or(() -> firstOwn(evidence, "GB_PRACTICAL_WITH_PEOPLE"))
                .filter(item -> evidence.selected(SkillTheme.PRACTICAL_APPLICATION))
                .ifPresent(item -> candidates.add(new Candidate("T2_SEEK_FEEDBACK_ON_PRACTICAL_WORK", 2, "Ask for "
                        + "feedback on your practical work in " + item.title() + ", and act on one point before the "
                        + "next assessment.", item.moduleIds(), List.of(SkillTheme.PRACTICAL_APPLICATION), false)));
        if (evidence.selected(SkillTheme.CRITICAL_EVALUATION)) {
            firstOwn(evidence, "GB_JUDGEMENT").or(() -> firstOwn(evidence, "GB_PEOPLE")).ifPresent(item ->
                    candidates.add(new Candidate("T3_FORM_YOUR_OWN_VIEW_FIRST", 3, "In " + item.title() + ", form and "
                            + "write down your own interpretation before you consult AI, then compare the two.",
                            item.moduleIds(), List.of(SkillTheme.CRITICAL_EVALUATION), false)));
        }
        if (evidence.selected(SkillTheme.TECHNICAL_FOUNDATIONS) && !evidence.technical().isEmpty()) {
            UniversityGuidanceItem first = evidence.technical().getFirst();
            candidates.add(new Candidate("T4_SOLVE_FIRST_THEN_CHECK_WITH_AI", 4, "In " + first.title() + ", work "
                    + "problems yourself first, then use AI to check your method rather than to produce it.",
                    first.moduleIds(), List.of(SkillTheme.TECHNICAL_FOUNDATIONS), false));
        }
        if (evidence.selected(SkillTheme.COMMUNICATION_AND_COLLABORATION)) {
            List<Module> people = evidence.where(module -> high(module.interpersonal()));
            candidates.add(new Candidate("T5_GET_FEEDBACK_ON_COMMUNICATION", 5, "Use group, client or patient-facing "
                    + "work in " + people.getFirst().title() + " to get feedback on how you communicate.",
                    List.of(people.getFirst().id()), List.of(SkillTheme.COMMUNICATION_AND_COLLABORATION), false));
        }
        if (evidence.selected(SkillTheme.PROFESSIONAL_JUDGEMENT)) {
            Module first = evidence.where(Module::professional).getFirst();
            candidates.add(new Candidate("T6_CHECK_AI_AGAINST_TRUSTED_GUIDANCE", 6, "Compare one AI answer about "
                    + first.title() + " with your course or professional guidance, and note where they differ and "
                    + "which you would rely on.", List.of(first.id()), List.of(SkillTheme.PROFESSIONAL_JUDGEMENT), false));
        }
        candidates.add(new Candidate("T7_PRACTISE_AN_ALLOWED_AI_WORKFLOW", 7, "Use one piece of coursework to practise "
                + "an AI-supported workflow that your module rules allow, and reflect on what it did and did not help "
                + "with.", List.of(), List.of(SkillTheme.AI_LITERACY), true));
        return candidates;
    }

    private static List<Candidate> next6To12Months(Evidence evidence) {
        List<Candidate> candidates = new ArrayList<>();
        List<Module> integrity = evidence.where(Module::integrity);
        List<Module> practical = evidence.where(module -> module.category() == PRACTICAL_OR_EXPERIENTIAL);
        if (evidence.selected(SkillTheme.INDEPENDENT_WORK) || evidence.selected(SkillTheme.PRACTICAL_APPLICATION)) {
            List<Module> examples = evidence.selected(SkillTheme.INDEPENDENT_WORK) ? integrity : practical;
            candidates.add(new Candidate("M1_BUILD_A_PORTFOLIO_OF_YOUR_OWN_WORK", 1, "Build a small portfolio of your "
                    + "own work from " + names(examples) + ", showing your process as well as the result.",
                    ids(examples), List.of(SkillTheme.INDEPENDENT_WORK, SkillTheme.PRACTICAL_APPLICATION), false));
        }
        if (evidence.selected(SkillTheme.PRACTICAL_APPLICATION) || evidence.selected(SkillTheme.PROFESSIONAL_JUDGEMENT)) {
            List<Module> basis = practical.isEmpty() ? evidence.where(Module::professional) : practical;
            candidates.add(new Candidate("M2_ADD_PRACTICAL_EXPERIENCE", 2, "Look for placements, projects or "
                    + "volunteering that add further hands-on experience alongside " + names(basis) + ".", ids(basis),
                    List.of(SkillTheme.PRACTICAL_APPLICATION, SkillTheme.PROFESSIONAL_JUDGEMENT), false));
        }
        if (evidence.selected(SkillTheme.TECHNICAL_FOUNDATIONS) && !evidence.technical().isEmpty()) {
            List<Module> technical = evidence.technical().stream().map(item -> evidence.module(item.moduleIds()
                    .getFirst())).toList();
            candidates.add(new Candidate("M3_APPLY_YOUR_FOUNDATIONS_IN_A_PROJECT", 3, "Take on a project that applies "
                    + "what you learn in " + names(technical) + ", and check any AI-assisted parts yourself.",
                    ids(technical), List.of(SkillTheme.TECHNICAL_FOUNDATIONS), false));
        }
        if (evidence.selected(SkillTheme.CRITICAL_EVALUATION)
                || evidence.selected(SkillTheme.COMMUNICATION_AND_COLLABORATION)) {
            List<Module> judgement = evidence.where(module -> module.category() == HUMAN_JUDGEMENT_LED);
            List<Module> people = evidence.where(module -> high(module.interpersonal()));
            boolean repeated = judgement.size() >= THEME_MINIMUM_MODULES || people.size() >= THEME_MINIMUM_MODULES;
            List<Module> basis = judgement.size() >= people.size() ? judgement : people;
            // The v2 change: with no judgement-led or interpersonal module to name, M4 has no evidence and is omitted.
            if (!basis.isEmpty()) candidates.add(new Candidate("M4_COLLECT_EXAMPLES_OF_YOUR_JUDGEMENT", 4, "Collect examples of decisions, "
                    + "interpretations or communication you handled yourself in " + names(basis) + ", so you can "
                    + "describe your judgement to others.", ids(basis), List.of(SkillTheme.CRITICAL_EVALUATION,
                    SkillTheme.COMMUNICATION_AND_COLLABORATION), !repeated));
        }
        if (evidence.selected(SkillTheme.PROFESSIONAL_JUDGEMENT)) {
            Module first = evidence.where(Module::professional).getFirst();
            candidates.add(new Candidate("M5_FOLLOW_PROFESSIONAL_GUIDANCE_ON_AI", 5, "Follow how professional guidance "
                    + "in your field treats AI use, and how it applies to work like " + first.title() + ".",
                    List.of(first.id()), List.of(SkillTheme.PROFESSIONAL_JUDGEMENT), false));
        }
        if (evidence.selected(SkillTheme.AI_LITERACY)) {
            candidates.add(new Candidate("M6_COMBINE_YOUR_SUBJECT_WITH_AI", 6, "Develop one way of combining your "
                    + "subject knowledge with AI tools that you could explain and justify to someone else.", List.of(),
                    List.of(SkillTheme.AI_LITERACY), aiPattern(evidence) < THEME_MINIMUM_MODULES));
        }
        candidates.add(new Candidate("M7_PLAN_HOW_TO_SHOW_YOUR_CAPABILITY", 7, "Look back at which modules built your "
                + "own capability most, and plan how you would show that capability to others.", List.of(),
                List.of(SkillTheme.INDEPENDENT_WORK), true));
        return candidates;
    }

    /**
     * Assessment integrity matters to the plan when independent work is a selected priority, or an integrity-sensitive
     * module is one where AI's role defines the module (independent written or project work, not hands-on practice).
     */
    private static boolean integrityMatters(Evidence evidence) {
        return evidence.selected(SkillTheme.INDEPENDENT_WORK)
                || !evidence.where(module -> module.integrity() && aiDefined(module)).isEmpty();
    }

    /** Modules showing a real AI-learning pattern: AI-defined modules and technical-core modules with HIGH assistance. */
    private static int aiPattern(Evidence evidence) {
        return evidence.where(module -> high(module.ai()) && (aiDefined(module) || module.category() == TECHNICAL_CORE))
                .size();
    }

    private static Optional<UniversityGuidanceItem> firstOwn(Evidence evidence, String ruleId) {
        return evidence.ownCapability().stream().filter(item -> item.ruleId().equals(ruleId)).findFirst();
    }

    /**
     * Evidence-backed actions first, ordered by the best rank among the selected themes they serve, then their fixed
     * position. A fallback (an action that met only its looser gate) is used only to reach the minimum of two, and the
     * most relevant fallback by theme rank goes first.
     */
    private static List<UniversityGuidanceAction> actions(Horizon horizon, List<Candidate> candidates,
            Map<SkillTheme, Integer> rank) {
        Function<Candidate, Integer> bestRank = candidate -> candidate.themes().stream()
                .map(theme -> rank.getOrDefault(theme, Integer.MAX_VALUE)).min(Integer::compare)
                .orElse(Integer.MAX_VALUE);
        Comparator<Candidate> order = Comparator.comparing(bestRank).thenComparingInt(Candidate::position);
        List<Candidate> chosen = new ArrayList<>(candidates.stream().filter(candidate -> !candidate.fallback())
                .sorted(order).limit(MAXIMUM_ACTIONS).toList());
        for (Candidate fallback : candidates.stream().filter(Candidate::fallback).sorted(order).toList()) {
            if (chosen.size() < MINIMUM_ACTIONS) {
                chosen.add(fallback);
            }
        }
        return chosen.stream().map(candidate -> new UniversityGuidanceAction(horizon.name().toLowerCase() + "-"
                + candidate.ruleId().substring(0, candidate.ruleId().indexOf('_')).toLowerCase(), horizon,
                candidate.ruleId(), candidate.text(), candidate.moduleIds(), candidate.themes(), candidate.fallback()))
                .toList();
    }

    // ---- shared helpers ----

    /** Picks up to {@code limit} in rank order, at most two per reason first, then fills from the rest in rank order. */
    private static <K> List<Module> spread(List<Module> ranked, Function<Module, K> reason, int limit) {
        Map<K, Integer> used = new LinkedHashMap<>();
        Set<Module> chosen = new LinkedHashSet<>();
        for (Module module : ranked) {
            if (chosen.size() >= limit) break;
            if (used.merge(reason.apply(module), 1, Integer::sum) <= FIRST_PASS_PER_REASON) chosen.add(module);
        }
        for (Module module : ranked) {
            if (chosen.size() >= limit) break;
            chosen.add(module);
        }
        List<Module> inRankOrder = new ArrayList<>(chosen);
        inRankOrder.sort(Comparator.comparingInt(ranked::indexOf));
        return inRankOrder;
    }

    private static UniversityGuidanceItem item(String id, Section section, String ruleId, String title, String text,
            List<Module> modules) {
        Map<String, Double> evidence = new LinkedHashMap<>();
        if (modules.size() == 1) {
            Module module = modules.getFirst();
            evidence.put("ai_learning_assistance", module.ai());
            evidence.put("independent_demonstration", module.independent());
            evidence.put("human_judgement", module.judgement());
            evidence.put("interpersonal_work", module.interpersonal());
            evidence.put("practical_application", module.practical());
            evidence.put("technical_quantitative", module.technical());
            evidence.put("professional_standards", module.standards());
            evidence.put(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE.name(), module.integrity() ? 1.0 : 0.0);
            evidence.put(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT.name(), module.professional() ? 1.0 : 0.0);
        }
        else {
            evidence.put("supportingModules", (double) modules.size());
        }
        return new UniversityGuidanceItem(id, section, ruleId, title, text, ids(modules),
                modules.stream().map(Module::category).toList(), evidence);
    }

    private static List<Integer> ids(List<Module> modules) {
        return modules.stream().map(Module::id).sorted().toList();
    }

    /** "A", "A and B", "A, B and C", or "A, B, C and 2 other modules", in course order. */
    static String names(List<Module> modules) {
        List<String> titles = modules.stream().sorted(Comparator.comparingInt(Module::id)).map(Module::title).toList();
        if (titles.size() <= NAMED_MODULES) {
            if (titles.size() == 1) return titles.getFirst();
            return String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.getLast();
        }
        int others = titles.size() - NAMED_MODULES;
        return String.join(", ", titles.subList(0, NAMED_MODULES)) + " and " + others + " other module"
                + (others == 1 ? "" : "s");
    }

    private static boolean high(double value) {
        return value >= HIGH - TOLERANCE;
    }

    /** The guidance-relevant view of one analysed module: frozen classification and judgment values only. */
    record Module(int id, String title, UniversityModuleCategory category, boolean integrity, boolean professional,
            double ai, double independent, double judgement, double interpersonal, double practical, double technical,
            double standards) {

        static Module of(UniversityModuleAnalysisItem item) {
            var judgments = item.judgments();
            var classification = item.classification();
            return new Module(item.moduleId(), judgments.moduleTitle(), classification.category(),
                    classification.hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE),
                    classification.hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT),
                    judgments.aiLearningAssistance().value(), judgments.independentDemonstration().value(),
                    judgments.humanJudgement().value(), judgments.interpersonalWork().value(),
                    judgments.practicalApplication().value(), judgments.technicalQuantitative().value(),
                    judgments.professionalStandards());
        }
    }
}
