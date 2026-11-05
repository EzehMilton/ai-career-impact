package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.narrative.professional.paid.PlanNoteRestatement;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ActionPlanNote;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.AiStudyStrategy;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.HumanCapabilityInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.SkillInsight;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;
import com.chikere.futureai.trace.TraceStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deterministic guardrail for {@value UniversityPaidNarrativeVersions#V2}. The deterministic findings are final, so
 * the narrative must keep every module, skill and action id in order and must not reclassify, rerank or contradict
 * anything. Its additions (learning uses, independent capability, study experiments, skill interpretation, plan notes
 * and the AI-study strategy) are held to list sizes and lengths, must differ from module to module, must add to the
 * fixed guidance rather than restate it, and must use vocabulary the course as described supports.
 *
 * <p>The checks are deliberately conservative pattern rules, not a language model: they catch the claims the
 * product must never make and the copy that would read the same for every module, and leave tone and quality to the
 * prompt. Anything rejected is repaired once; if the repair also fails, the report uses deterministic copy.</p>
 */
public final class UniversityPaidNarrativeValidator {

    public static final String PRESENT = "NARRATIVE_FIELDS_PRESENT";
    public static final String WELL_FORMED = "WELL_FORMED_STRUCTURED_OUTPUT";
    public static final String IDS = "IDS_MATCH_DETERMINISTIC_ORDER";
    /** A module entry describes the module its id is paired with, not another supplied module. */
    public static final String BINDING = "ENTRY_DESCRIBES_ITS_PAIRED_MODULE";
    public static final String LENGTH = "LIST_SIZES_AND_LENGTHS";
    public static final String NUMERIC = "NO_NUMBERS_OR_SCORES";
    public static final String SALARY = "NO_SALARY_OR_PAY";
    public static final String EMPLOYMENT = "NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION";
    public static final String AI_CERTAINTY = "NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO";
    public static final String AI_PERMISSION = "NO_AI_PERMISSION_CLAIM";
    public static final String INSTITUTION_RULES = "NO_INVENTED_INSTITUTIONAL_RULES";
    public static final String MEASURE_NAMES = "ONLY_THE_THREE_COURSE_PROFILE_MEASURES";
    public static final String BANDS = "BANDS_MATCH_THE_COURSE_PROFILE";
    public static final String OVERALL = "NO_OVERALL_SCORE_OR_RANKING";
    public static final String REGULATION = "NO_UNSUPPORTED_REGULATION_CLAIM";
    public static final String RECLASSIFICATION = "NO_MODULE_RECLASSIFICATION";
    public static final String VENDORS = "NO_NAMED_PRODUCTS_OR_VENDORS";
    public static final String VOCABULARY = "VOCABULARY_FITS_THE_COURSE";
    public static final String GENERIC = "NO_GENERIC_REPEATED_OR_RESTATED_CONTENT";

    public static final List<String> RULES = List.of(PRESENT, WELL_FORMED, IDS, BINDING, LENGTH, NUMERIC, SALARY, EMPLOYMENT,
            AI_CERTAINTY, AI_PERMISSION, INSTITUTION_RULES, MEASURE_NAMES, BANDS, OVERALL, REGULATION,
            RECLASSIFICATION, VENDORS, VOCABULARY, GENERIC);
    /** Every rule except a missing narrative: rejected prose gets its one repair. */
    public static final Set<String> REPAIRABLE = Set.copyOf(RULES.stream().filter(rule -> !rule.equals(PRESENT))
            .toList());

    /** Inclusive bounds on the lists; each matches the prompt's FIELDS section. */
    static final int MIN_USES = 2;
    static final int MAX_USES = 4;
    static final int MIN_INDEPENDENT = 1;
    static final int MAX_INDEPENDENT = 3;
    static final int MIN_IN_THIS_COURSE = 2;
    static final int MAX_IN_THIS_COURSE = 3;
    static final int MIN_STRATEGY = 1;
    static final int MAX_STRATEGY = 3;
    /** Character bounds: long enough to be specific, short enough to stay a concise report line. */
    static final int MIN_TEXT = 12;
    static final int MAX_ITEM = 240;
    static final int MAX_EXPERIMENT = 420;
    static final int MAX_EXPLANATION = 600;
    static final int MAX_SUMMARY = 1600;
    static final int MAX_SECTION = 900;
    /** Two modules' learning uses sharing at least this share of their content words read as one template. */
    static final double MAX_USE_OVERLAP = 0.6;

    private static final Pattern DIGIT = Pattern.compile("\\d");
    // Income and wages only as the student's or graduates' own pay: in economics they are subject matter.
    private static final Pattern CURRENCY = Pattern.compile(
            "(?i)[£$€]|\\b(?:salary|salaries|pay\\s+(?:of|rise|level)|earn(?:s|ing)?\\b"
            + "|(?:your|graduates?(?:'|’)?|graduate|starting|future)\\s+(?:incomes?|wages?))");
    private static final Pattern EMPLOYMENT_CLAIM = Pattern.compile("(?i)\\b(?:guarantee\\w*|will\\s+(?:get|find|secure"
            + "|land)\\s+(?:you\\s+)?(?:a\\s+)?(?:job|work|employment|role)|employab(?:le|ility)\\s+(?:is|will)"
            + "|job\\s+(?:security|prospects|loss|losses)|obsolete|indispensab\\w*"
            // Unemployment only as a prediction about the student: in economics it is subject matter.
            + "|(?:you|graduates?|students?)\\s+(?:will|may|could|might|would)\\s+(?:\\w+\\s+){0,2}(?:be|become|face)"
            + "\\s+unemploy\\w*|(?:be|become|end\\s+up)\\s+unemployed|risk(?:s|ing)?\\s+unemployment"
            // Employment redundancy only: "data redundancy" is ordinary database vocabulary.
            + "|(?:made|be|being|been|become)\\s+redundant|(?:job|staff|workforce|compulsory)\\s+redundanc\\w*"
            + "|redundanc(?:y|ies)\\s+(?:risk|threat|programme)"
            + "|irreplaceab\\w*|(?:jobs?|roles?|professions?|occupations?|careers?)\\s+(?:will|may|could|are\\s+going\\s+to)"
            + "\\s+(?:disappear|vanish|be\\s+replaced|be\\s+automated)"
            + "|replace[sd]?\\s+(?:you|graduates|students|workers|people|professionals)"
            // "AI cannot replace human judgement": a replacement claim even when negated.
            + "|(?:cannot|can\\s*not|can't|will\\s+not|won't)\\s+(?:fully\\s+|ever\\s+)?replace\\b"
            + "|(?:cannot|can\\s*not|can't|will\\s+not|won't)\\s+be\\s+replaced\\b"
            + "|(?:take|taking|took)\\s+(?:your|their)\\s+jobs?|in\\s+demand\\s+by\\s+employers)");
    private static final Pattern AI_CERTAINTY_CLAIM = Pattern.compile("(?i)\\b(?:ai|machines?|computers?)\\s+"
            + "(?:cannot|can\\s*not|can't|will\\s+never|could\\s+never)\\b|\\bai[-\\s]?proof\\b|\\bfuture[-\\s]?proof\\b"
            + "|\\bsafe\\s+from\\s+(?:ai|automation)\\b|\\bautomation[-\\s]?proof\\b|\\bimmune\\s+to\\s+(?:ai|automation)\\b"
            + "|\\b(?:beyond|out\\s+of)\\s+(?:the\\s+)?reach\\s+of\\s+ai\\b");
    private static final Pattern AI_PERMISSION_CLAIM = Pattern.compile("(?i)\\b(?:allowed|permitted|free|fine|"
            + "acceptable|ok(?:ay)?)\\s+to\\s+(?:use|submit)\\s+ai\\b|\\bai\\s+(?:use\\s+)?is\\s+(?:allowed|permitted|"
            + "acceptable)\\b|\\b(?:submit|hand\\s+in)\\s+(?:ai[-\\s]generated|ai[-\\s]written)\\b"
            + "|\\b(?:let|have)\\s+ai\\s+(?:write|complete|do)\\s+(?:your|the)\\s+(?:assessed|assessment|coursework"
            + "|essay|dissertation|project)");
    /** What an institution allows, bans or detects: never supplied, so never stated. */
    private static final Pattern INSTITUTION_RULE_CLAIM = Pattern.compile("(?i)\\b(?:your|the)\\s+(?:university|"
            + "institution|department|faculty|school)(?:'s|’s)?\\s+(?:(?:AI|assessment|academic)\\s+)?(?:policy\\s+|rules\\s+)?"
            + "(?:allows?|permits?|prohibits?|forbids?|bans?|requires?|says|states)\\b"
            + "|\\b(?:is|are)\\s+(?:banned|prohibited|forbidden|not\\s+allowed|not\\s+permitted)\\s+in\\s+(?:your\\s+|the\\s+)?"
            + "(?:exams?|assessments?|coursework|assignments?)\\b|\\bacademic\\s+misconduct\\b|\\bAI[-\\s]detect\\w*"
            + "|\\b(?:will|would)\\s+be\\s+(?:flagged|detected|penalised|penalized)\\b");
    private static final Pattern SUPERSEDED_MEASURES = Pattern.compile("(?i)\\b(?:future\\s+readiness|career\\s+"
            + "adaptability|human\\s+differentiation|skill\\s+transferability|ai\\s+complementarity)\\b");
    private static final Pattern OVERALL_CLAIM = Pattern.compile("(?i)\\b(?:overall\\s+(?:score|rating|grade|result)"
            + "|strongest\\s+measure|weakest\\s+measure|ranks?\\s+(?:among|above|below)|ranked\\b|league\\s+table"
            + "|out\\s+of\\s+ten|(?:most|least)\\s+important\\s+module|more\\s+important\\s+than)");
    /** Calling the course or its profession regulated; market or public-policy regulation is subject matter. */
    private static final Pattern REGULATION_CLAIM = Pattern.compile("(?i)\\bregulated\\s+(?:course|degree|programme"
            + "|profession|occupation|field)s?\\b|\\b(?:this|the|your)\\s+(?:course|degree|programme|profession|field)"
            + "\\s+is\\s+(?:a\\s+)?regulated\\b|\\b(?:professional|statutory)\\s+regulat(?:ion|or|ors)\\b"
            + "|\\bregulatory\\s+(?:body|bodies|registration)\\b");
    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(?:ChatGPT|GPT-?\\d\\w*|OpenAI|Copilot|Gemini|Bard"
            + "|Claude|Anthropic|Llama|Mistral|Perplexity|Grammarly|Quizlet|Anki|Chegg|Wolfram|Notion|Otter|Jasper"
            + "|Microsoft|Google|Amazon|AWS|Azure|Turnitin)\\b");
    /**
     * Programming vocabulary, rejected when the course as described involves no programming: "AI-generated code" in a
     * nursing report is the old generic template, not this course. Not "the Code" or "code of conduct", which are
     * professional standards.
     */
    private static final Pattern PROGRAMMING_TERMS = Pattern.compile("\\b(?:(?<![Tt]he\\s)code(?!\\s+of\\b)|source\\s+code"
            + "|[Dd]ebug\\w*|SQL|[Cc]ompil(?:e|er|ing)|[Pp]rogramming)\\b");
    private static final String BAND_WORDS = "(low|lower|moderate|high|higher|strong|weak|limited)";
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");

    /** The fixed guidance phrases v1 repeated for every module of a kind, normalised: never an example of a use. */
    private static final List<String> GENERIC_GUIDANCE_COPY = List.of(
            "explaining concepts generating practice problems and helping you check your own working",
            "explaining ideas comparing perspectives and revising key material",
            "summarising reading exploring different interpretations and testing your arguments",
            "brainstorm research and plan",
            "support preparation and revision");

    /**
     * Generic study vocabulary plus function words. A use or explanation whose every content word (beyond its module's
     * own title) is one of these names no reasoning, material or task of the module: it is the template with the
     * title inserted, whichever module it is written for.
     */
    private static final Set<String> GENERIC_STUDY_WORDS = Set.of(
            "explain", "explains", "explaining", "explanation", "explanations", "concept", "concepts", "idea", "ideas",
            "generate", "generates", "generating", "practice", "practise", "practising", "problem", "problems",
            "question", "questions", "check", "checks", "checking", "working", "work", "summarise", "summarises",
            "summarising", "summary", "summaries", "reading", "readings", "read", "explore", "explores", "exploring",
            "different", "interpretation", "interpretations", "test", "tests", "testing", "argument", "arguments",
            "revise", "revising", "revision", "material", "materials", "compare", "comparing", "perspective",
            "perspectives", "topic", "topics", "help", "helps", "helping", "support", "supports", "supporting",
            "using", "request", "requesting", "tool", "tools", "learning", "learn", "understand", "understanding",
            "discussion", "discuss", "notes", "content", "quiz", "quizzes", "quizzing", "answer", "answers", "core",
            "main", "important", "difficult", "complex", "course", "module", "modules", "subject", "theme", "themes",
            "your", "with", "that", "this", "these", "those", "from", "into", "them", "they", "their", "then", "could",
            "would", "should", "might", "will", "also", "more", "most", "other", "about", "when", "where", "what",
            "which", "while", "through", "ways", "well", "better", "issues", "clarify", "clarifies", "clarifying",
            "clarification");

    private final AssessmentTrace trace;

    public UniversityPaidNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    public UniversityPaidNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityPaidNarrative validate(UniversityPaidNarrative narrative, UniversityPaidNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.UNIVERSITY, RULES,
                TraceStage.PAID_NARRATIVE_VALIDATION);
        checks.check(PRESENT, empty(narrative), () -> null, "the provider returned no paid narrative");
        Optional<String> malformed = malformed(narrative);
        checks.check(WELL_FORMED, malformed.isPresent(), malformed::orElseThrow,
                "every narrative field and list item must be present");
        Optional<String> ids = idMismatch(narrative, context);
        checks.check(IDS, ids.isPresent(), ids::orElseThrow,
                "every module, skill and action id must appear exactly once, in the deterministic order, and nothing "
                        + "else");
        Optional<Located> misbound = misbound(narrative, context);
        checks.check(BINDING, misbound.isPresent(), () -> misbound.orElseThrow().text(),
                misbound.map(Located::location).orElse("each module entry must describe the module its id is paired "
                        + "with, not another supplied module"));
        Optional<String> sizes = sizeOrLength(narrative);
        checks.check(LENGTH, sizes.isPresent(), sizes::orElseThrow,
                "each list must have the stated number of items and every text a concise, specific length");

        // Titles are data the narrative may quote; numbers inside them ("Year 2 Project") are not claims.
        List<String> prose = withoutTitles(texts(narrative), context);
        claim(checks, NUMERIC, DIGIT, prose, "the narrative must not state numbers or scores");
        claim(checks, SALARY, CURRENCY, prose, "the narrative must not mention salary or pay");
        claim(checks, EMPLOYMENT, EMPLOYMENT_CLAIM, prose,
                "the narrative must not predict employment, job loss or replacement, or promise any outcome");
        claim(checks, AI_CERTAINTY, AI_CERTAINTY_CLAIM, prose,
                "the narrative must not claim what AI cannot do or that any work is safe from AI");
        claim(checks, AI_PERMISSION, AI_PERMISSION_CLAIM, prose,
                "the narrative must not suggest AI use is permitted in assessed work");
        claim(checks, INSTITUTION_RULES, INSTITUTION_RULE_CLAIM, prose,
                "the narrative must not state what an institution allows, bans or detects");
        claim(checks, MEASURE_NAMES, SUPERSEDED_MEASURES, prose,
                "the narrative must use only the three course-profile measures");
        String mismatch = bandMismatch(prose, context);
        checks.check(BANDS, mismatch != null, () -> mismatch, "every band mentioned must match the course profile");
        claim(checks, OVERALL, OVERALL_CLAIM, prose,
                "the narrative must not create an overall score or rank modules or measures");
        checks.check(REGULATION, !context.professionalStandardsRelevant()
                        && prose.stream().anyMatch(text -> REGULATION_CLAIM.matcher(text).find()),
                () -> sentence(REGULATION_CLAIM, prose),
                "the narrative must not call the course regulated unless professional standards are relevant");
        Optional<String> reclassified = reclassification(narrative, context);
        checks.check(RECLASSIFICATION, reclassified.isPresent(), reclassified::orElseThrow,
                "a module must not be described with a classification other than its stored one");
        Optional<String> vendor = unsupplied(prose, VENDOR, context);
        checks.check(VENDORS, vendor.isPresent(), vendor::orElseThrow,
                "the narrative must not name AI products, study apps or companies");
        Optional<String> vocabulary = context.programmingVocabularySupported() ? Optional.empty()
                : Optional.ofNullable(sentence(PROGRAMMING_TERMS, prose));
        checks.check(VOCABULARY, vocabulary.isPresent(), vocabulary::orElseThrow,
                "programming vocabulary such as code, debugging or SQL does not fit a course that involves no "
                        + "programming");
        Optional<GenericFinding> generic = genericOrRestated(narrative, context);
        checks.check(GENERIC, generic.isPresent(), () -> generic.orElseThrow().rejected(),
                generic.map(GenericFinding::reason).orElse("content must be specific to each module and add to the "
                        + "fixed guidance: no repeated or near-identical items, no generic template copy and no note "
                        + "that only restates its action"));
        checks.accepted();
        return narrative;
    }

    /** No narrative, or nothing in it: a provider failure, not prose to repair. */
    private static boolean empty(UniversityPaidNarrative narrative) {
        if (narrative == null) {
            return true;
        }
        boolean noText = Stream.of(narrative.executiveSummary(), narrative.measuresTogether(), narrative.coursePattern(),
                narrative.closingPerspective()).allMatch(text -> text == null || text.isBlank());
        boolean noLists = Stream.of(narrative.moduleLearningInsights(), narrative.humanCapabilityInsights(),
                narrative.skillInsights(), narrative.actionPlanNotes()).allMatch(list -> list == null || list.isEmpty());
        return noText && noLists && narrative.aiStudyStrategy() == null;
    }

    private static Optional<String> malformed(UniversityPaidNarrative narrative) {
        List<String> missing = new ArrayList<>();
        text("executiveSummary", narrative.executiveSummary(), missing);
        text("measuresTogether", narrative.measuresTogether(), missing);
        text("coursePattern", narrative.coursePattern(), missing);
        text("closingPerspective", narrative.closingPerspective(), missing);
        items("moduleLearningInsights", narrative.moduleLearningInsights(), missing, (name, item) -> {
            text(name + ".moduleId", item.moduleId(), missing);
            text(name + ".explanation", item.explanation(), missing);
            strings(name + ".aiLearningUses", item.aiLearningUses(), missing);
            strings(name + ".independentCapability", item.independentCapability(), missing);
            text(name + ".studyExperiment", item.studyExperiment(), missing);
        });
        items("humanCapabilityInsights", narrative.humanCapabilityInsights(), missing, (name, item) -> {
            text(name + ".moduleId", item.moduleId(), missing);
            text(name + ".explanation", item.explanation(), missing);
            text(name + ".independentPractice", item.independentPractice(), missing);
        });
        items("skillInsights", narrative.skillInsights(), missing, (name, item) -> {
            text(name + ".skillId", item.skillId(), missing);
            text(name + ".explanation", item.explanation(), missing);
            strings(name + ".inThisCourseThisMeans", item.inThisCourseThisMeans(), missing);
        });
        items("actionPlanNotes", narrative.actionPlanNotes(), missing, (name, item) -> {
            text(name + ".actionId", item.actionId(), missing);
            text(name + ".explanation", item.explanation(), missing);
        });
        AiStudyStrategy strategy = narrative.aiStudyStrategy();
        if (strategy == null) {
            missing.add("aiStudyStrategy");
        }
        else {
            strings("aiStudyStrategy.useAi", strategy.useAi(), missing);
            strings("aiStudyStrategy.learnWithoutAi", strategy.learnWithoutAi(), missing);
            strings("aiStudyStrategy.verify", strategy.verify(), missing);
            strings("aiStudyStrategy.demonstrateOwnCapability", strategy.demonstrateOwnCapability(), missing);
            strings("aiStudyStrategy.buildEvidence", strategy.buildEvidence(), missing);
        }
        return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing));
    }

    private interface ItemCheck<T> {
        void check(String name, T item);
    }

    private static <T> void items(String name, List<T> values, List<String> missing, ItemCheck<T> check) {
        if (values == null) {
            missing.add(name);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            T item = values.get(index);
            if (item == null) {
                missing.add(name + "[" + index + "]");
            }
            else {
                check.check(name + "[" + index + "]", item);
            }
        }
    }

    private static void text(String name, String value, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    private static void strings(String name, List<String> values, List<String> missing) {
        if (values == null) {
            missing.add(name);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            text(name + "[" + index + "]", values.get(index), missing);
        }
    }

    private static Optional<String> idMismatch(UniversityPaidNarrative narrative, UniversityPaidNarrativeContext context) {
        return Stream.of(
                        compare("moduleLearningInsights", narrative.moduleLearningInsights().stream()
                                .map(ModuleLearningInsight::moduleId).toList(), context.moduleLearningIds()),
                        compare("humanCapabilityInsights", narrative.humanCapabilityInsights().stream()
                                .map(HumanCapabilityInsight::moduleId).toList(), context.humanCapabilityIds()),
                        compare("skillInsights", narrative.skillInsights().stream().map(SkillInsight::skillId).toList(),
                                context.skillIds()),
                        compare("actionPlanNotes", narrative.actionPlanNotes().stream().map(ActionPlanNote::actionId)
                                .toList(), context.actionIds()))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> compare(String field, List<String> ids, List<String> expected) {
        List<String> actual = ids.stream().map(String::strip).toList();
        if (actual.equals(expected)) {
            return Optional.empty();
        }
        Set<String> known = new HashSet<>(expected);
        Optional<String> unknown = actual.stream().filter(id -> !known.contains(id)).findFirst();
        if (unknown.isPresent()) {
            return Optional.of(field + ": unknown id " + unknown.get());
        }
        if (new HashSet<>(actual).size() != actual.size()) {
            return Optional.of(field + ": an id appears more than once");
        }
        Optional<String> missing = expected.stream().filter(id -> !actual.contains(id)).findFirst();
        if (missing.isPresent()) {
            return Optional.of(field + ": missing id " + missing.get());
        }
        return Optional.of(field + ": ids are not in the deterministic order " + expected);
    }

    /** Every list within its bounds and every text within its length; the message names the first that is not. */
    private static Optional<String> sizeOrLength(UniversityPaidNarrative narrative) {
        List<String> problems = new ArrayList<>();
        length("executiveSummary", narrative.executiveSummary(), MAX_SUMMARY, problems);
        length("measuresTogether", narrative.measuresTogether(), MAX_SECTION, problems);
        length("coursePattern", narrative.coursePattern(), MAX_SECTION, problems);
        length("closingPerspective", narrative.closingPerspective(), MAX_SECTION, problems);
        for (ModuleLearningInsight item : narrative.moduleLearningInsights()) {
            length(item.moduleId() + ".explanation", item.explanation(), MAX_EXPLANATION, problems);
            list(item.moduleId() + ".aiLearningUses", item.aiLearningUses(), MIN_USES, MAX_USES, problems);
            list(item.moduleId() + ".independentCapability", item.independentCapability(), MIN_INDEPENDENT,
                    MAX_INDEPENDENT, problems);
            length(item.moduleId() + ".studyExperiment", item.studyExperiment(), MAX_EXPERIMENT, problems);
        }
        for (HumanCapabilityInsight item : narrative.humanCapabilityInsights()) {
            length(item.moduleId() + ".explanation", item.explanation(), MAX_EXPLANATION, problems);
            length(item.moduleId() + ".independentPractice", item.independentPractice(), MAX_EXPERIMENT, problems);
        }
        for (SkillInsight item : narrative.skillInsights()) {
            length(item.skillId() + ".explanation", item.explanation(), MAX_EXPLANATION, problems);
            list(item.skillId() + ".inThisCourseThisMeans", item.inThisCourseThisMeans(), MIN_IN_THIS_COURSE,
                    MAX_IN_THIS_COURSE, problems);
        }
        narrative.actionPlanNotes().forEach(item -> length(item.actionId(), item.explanation(), MAX_EXPLANATION,
                problems));
        AiStudyStrategy strategy = narrative.aiStudyStrategy();
        list("aiStudyStrategy.useAi", strategy.useAi(), MIN_STRATEGY, MAX_STRATEGY, problems);
        list("aiStudyStrategy.learnWithoutAi", strategy.learnWithoutAi(), MIN_STRATEGY, MAX_STRATEGY, problems);
        list("aiStudyStrategy.verify", strategy.verify(), MIN_STRATEGY, MAX_STRATEGY, problems);
        list("aiStudyStrategy.demonstrateOwnCapability", strategy.demonstrateOwnCapability(), MIN_STRATEGY,
                MAX_STRATEGY, problems);
        list("aiStudyStrategy.buildEvidence", strategy.buildEvidence(), MIN_STRATEGY, MAX_STRATEGY, problems);
        return problems.stream().findFirst();
    }

    private static void list(String name, List<String> values, int minimum, int maximum, List<String> problems) {
        if (values.size() < minimum || values.size() > maximum) {
            problems.add(name + ": " + values.size() + " items, expected " + minimum + "-" + maximum);
        }
        values.forEach(value -> length(name, value, MAX_ITEM, problems));
    }

    private static void length(String name, String value, int maximum, List<String> problems) {
        int length = value.strip().length();
        if (length < MIN_TEXT || length > maximum) {
            problems.add(name + ": " + length + " characters, expected " + MIN_TEXT + "-" + maximum);
        }
    }

    /** Every prose field, in report order. */
    private static List<String> texts(UniversityPaidNarrative narrative) {
        List<String> texts = new ArrayList<>(List.of(narrative.executiveSummary(), narrative.measuresTogether(),
                narrative.coursePattern()));
        for (ModuleLearningInsight item : narrative.moduleLearningInsights()) {
            texts.add(item.explanation());
            texts.addAll(item.aiLearningUses());
            texts.addAll(item.independentCapability());
            texts.add(item.studyExperiment());
        }
        narrative.humanCapabilityInsights().forEach(item -> texts.addAll(List.of(item.explanation(),
                item.independentPractice())));
        for (SkillInsight item : narrative.skillInsights()) {
            texts.add(item.explanation());
            texts.addAll(item.inThisCourseThisMeans());
        }
        narrative.actionPlanNotes().forEach(item -> texts.add(item.explanation()));
        texts.addAll(strategyItems(narrative.aiStudyStrategy()));
        texts.add(narrative.closingPerspective());
        return texts;
    }

    private static List<String> strategyItems(AiStudyStrategy strategy) {
        return Stream.of(strategy.useAi(), strategy.learnWithoutAi(), strategy.verify(),
                strategy.demonstrateOwnCapability(), strategy.buildEvidence()).flatMap(Collection::stream).toList();
    }

    private static void claim(NarrativeRuleChecks checks, String rule, Pattern pattern, List<String> prose,
            String rejection) {
        checks.check(rule, prose.stream().anyMatch(text -> pattern.matcher(text).find()),
                () -> sentence(pattern, prose), rejection);
    }

    /** A band word immediately before a measure name ("high Transferable Capability") must match its band. */
    private static String bandMismatch(List<String> prose, UniversityPaidNarrativeContext context) {
        for (var measure : context.measures()) {
            Pattern mention = Pattern.compile("(?i)\\b" + BAND_WORDS + "(?:\\s+(?:level\\s+of|degree\\s+of))?\\s+"
                    + Pattern.quote(measure.name()));
            for (String text : prose) {
                Matcher matcher = mention.matcher(text);
                while (matcher.find()) {
                    if (!sameBand(matcher.group(1), measure.band())) {
                        return matcher.group();
                    }
                }
            }
        }
        return null;
    }

    private static boolean sameBand(String word, String band) {
        String normalised = switch (word.toLowerCase(Locale.ROOT)) {
            case "high", "higher", "strong" -> "HIGH";
            case "moderate" -> "MODERATE";
            default -> "LOW";
        };
        return normalised.equals(band);
    }

    /**
     * A module's own texts naming another category. The distinctive labels count anywhere ("mainly practical or
     * experiential"); the two ending "remains central" are ordinary prose, so they count only when quoted.
     */
    private static Optional<String> reclassification(UniversityPaidNarrative narrative,
            UniversityPaidNarrativeContext context) {
        Map<String, String> classification = new LinkedHashMap<>();
        Map<String, List<String>> textsById = new LinkedHashMap<>();
        context.aiLearningModules().forEach(module -> classification.put("learning:" + module.id(),
                module.classification()));
        context.ownCapabilityModules().forEach(module -> classification.put("own:" + module.id(),
                module.classification()));
        for (ModuleLearningInsight item : narrative.moduleLearningInsights()) {
            List<String> texts = new ArrayList<>(List.of(item.explanation(), item.studyExperiment()));
            texts.addAll(item.aiLearningUses());
            texts.addAll(item.independentCapability());
            textsById.put("learning:" + item.moduleId().strip(), texts);
        }
        narrative.humanCapabilityInsights().forEach(item -> textsById.put("own:" + item.moduleId().strip(),
                List.of(item.explanation(), item.independentPractice())));
        for (var entry : textsById.entrySet()) {
            String stored = classification.get(entry.getKey());
            for (String text : entry.getValue()) {
                String lower = text.toLowerCase(Locale.ROOT);
                for (UniversityModuleCategory other : UniversityModuleCategory.values()) {
                    String label = other.customerLabel().toLowerCase(Locale.ROOT);
                    String named = label.endsWith("remains central") ? "\"" + label : label;
                    if (!other.customerLabel().equals(stored) && lower.contains(named)) {
                        return Optional.of(text.strip());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The first sentence naming something the student never mentioned in their own course text. */
    private static Optional<String> unsupplied(List<String> prose, Pattern names, UniversityPaidNarrativeContext context) {
        String own = context.groundingText();
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                Matcher matcher = names.matcher(sentence);
                while (matcher.find()) {
                    if (!own.contains(matcher.group().toLowerCase(Locale.ROOT))) {
                        return Optional.of(sentence.strip());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Where generic content was found, for the trace and the one repair: the field ({@code
     * moduleLearningInsights[module-5].explanation}), what is wrong with it, the rejected text and, when there is one,
     * the text it was compared against.
     */
    record GenericFinding(String location, String problem, String rejected, String comparedWith) {

        String reason() {
            return location + " " + problem + (comparedWith == null ? "" : ". Compared with: \"" + comparedWith.strip()
                    + "\"");
        }
    }

    /** One narrative text and the field it came from. */
    private record Located(String location, String text) {
    }

    /**
     * Copy that reads the same for every module, or that only repeats what the report already says: an item repeated
     * word for word across modules, two modules whose learning uses are nearly the same, the old category template or
     * only generic study words offered as a use or explanation, an explanation that restates its module's fixed
     * guidance, or a plan note that restates its action.
     */
    private static Optional<GenericFinding> genericOrRestated(UniversityPaidNarrative narrative,
            UniversityPaidNarrativeContext context) {
        List<ModuleLearningInsight> learning = narrative.moduleLearningInsights();
        String ml = "moduleLearningInsights";
        String hc = "humanCapabilityInsights";
        Optional<GenericFinding> repeated = Stream.of(
                        repeated(each(learning, ml, ModuleLearningInsight::moduleId, "aiLearningUses",
                                ModuleLearningInsight::aiLearningUses)),
                        repeated(each(learning, ml, ModuleLearningInsight::moduleId, "independentCapability",
                                ModuleLearningInsight::independentCapability)),
                        repeated(one(learning, ml, ModuleLearningInsight::moduleId, "studyExperiment",
                                ModuleLearningInsight::studyExperiment)),
                        repeated(one(learning, ml, ModuleLearningInsight::moduleId, "explanation",
                                ModuleLearningInsight::explanation)),
                        repeated(one(narrative.humanCapabilityInsights(), hc, HumanCapabilityInsight::moduleId,
                                "explanation", HumanCapabilityInsight::explanation)),
                        repeated(one(narrative.humanCapabilityInsights(), hc, HumanCapabilityInsight::moduleId,
                                "independentPractice", HumanCapabilityInsight::independentPractice)),
                        repeated(each(narrative.skillInsights(), "skillInsights", SkillInsight::skillId,
                                "inThisCourseThisMeans", SkillInsight::inThisCourseThisMeans)),
                        repeated(one(narrative.actionPlanNotes(), "actionPlanNotes", ActionPlanNote::actionId,
                                "explanation", ActionPlanNote::explanation)))
                .flatMap(Optional::stream).findFirst();
        if (repeated.isPresent()) {
            return repeated;
        }
        Map<String, String> titles = new LinkedHashMap<>();
        context.aiLearningModules().forEach(module -> titles.put(module.id(), module.module()));
        for (int first = 0; first < learning.size(); first++) {
            for (int second = first + 1; second < learning.size(); second++) {
                Set<String> a = words(String.join(" ", learning.get(first).aiLearningUses()),
                        titles.get(learning.get(first).moduleId().strip()));
                Set<String> b = words(String.join(" ", learning.get(second).aiLearningUses()),
                        titles.get(learning.get(second).moduleId().strip()));
                if (overlap(a, b) >= MAX_USE_OVERLAP) {
                    return Optional.of(new GenericFinding(field(ml, learning.get(second).moduleId(), "aiLearningUses"),
                            "is too similar to " + field(ml, learning.get(first).moduleId(), "aiLearningUses")
                                    + ": the two modules' uses would still read sensibly with their names swapped",
                            String.join("; ", learning.get(second).aiLearningUses()),
                            String.join("; ", learning.get(first).aiLearningUses())));
                }
            }
        }
        Map<String, UniversityPaidNarrativeContext.GuidanceModule> learningModules = byId(context.aiLearningModules());
        for (ModuleLearningInsight item : learning) {
            var module = learningModules.get(item.moduleId().strip());
            if (module != null && PlanNoteRestatement.restates(item.explanation(), module.currentGuidance(),
                    module.classification(), module.module())) {
                return Optional.of(new GenericFinding(field(ml, item.moduleId(), "explanation"),
                        "restates the generic fallback guidance for this module", item.explanation().strip(),
                        module.currentGuidance()));
            }
        }
        for (ModuleLearningInsight item : learning) {
            String title = titles.get(item.moduleId().strip());
            List<Located> examples = new ArrayList<>();
            for (int index = 0; index < item.aiLearningUses().size(); index++) {
                examples.add(new Located(field(ml, item.moduleId(), "aiLearningUses[" + index + "]"),
                        item.aiLearningUses().get(index)));
            }
            examples.add(new Located(field(ml, item.moduleId(), "studyExperiment"), item.studyExperiment()));
            for (Located example : examples) {
                String flat = normalised(example.text());
                Optional<String> template = GENERIC_GUIDANCE_COPY.stream().filter(flat::contains).findFirst();
                if (template.isPresent()) {
                    return Optional.of(new GenericFinding(example.location(), "repeats the generic fallback template",
                            example.text().strip(), template.get()));
                }
            }
            List<Located> phrased = new ArrayList<>(examples.subList(0, item.aiLearningUses().size()));
            phrased.add(new Located(field(ml, item.moduleId(), "explanation"), item.explanation()));
            for (Located text : phrased) {
                if (onlyGenericWords(text.text(), title, context)) {
                    return Optional.of(new GenericFinding(text.location(), "uses only generic study wording with the "
                            + "module name inserted; it names no reasoning, material or task of this module",
                            text.text().strip(), null));
                }
            }
        }
        Map<String, UniversityPaidNarrativeContext.GuidanceModule> ownModules = byId(context.ownCapabilityModules());
        for (HumanCapabilityInsight item : narrative.humanCapabilityInsights()) {
            var module = ownModules.get(item.moduleId().strip());
            if (module != null && PlanNoteRestatement.restates(item.explanation(), module.currentGuidance(),
                    module.classification(), module.module())) {
                return Optional.of(new GenericFinding(field(hc, item.moduleId(), "explanation"),
                        "restates the fixed guidance shown above it", item.explanation().strip(),
                        module.currentGuidance()));
            }
        }
        var actions = context.actionsById();
        for (ActionPlanNote note : narrative.actionPlanNotes()) {
            var action = actions.get(note.actionId().strip());
            if (action != null && PlanNoteRestatement.restates(note.explanation(), action.action())) {
                return Optional.of(new GenericFinding(field("actionPlanNotes", note.actionId(), "explanation"),
                        "restates its action, which is shown directly above it", note.explanation().strip(),
                        action.action()));
            }
        }
        return Optional.empty();
    }

    /**
     * A module entry that explicitly names another supplied module: content written for one module under another's
     * id. Only the fields that describe one module are read, and only exact module titles count, never a guess at
     * what prose is about. The paired title is removed first, so a title inside it (Ethics in Ethics and Public
     * Policy) is never another module.
     */
    private static Optional<Located> misbound(UniversityPaidNarrative narrative, UniversityPaidNarrativeContext context) {
        List<String> titles = context.modules().stream().map(UniversityPaidNarrativeContext.Module::title).distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        Map<String, UniversityPaidNarrativeContext.GuidanceModule> learning = byId(context.aiLearningModules());
        for (ModuleLearningInsight item : narrative.moduleLearningInsights()) {
            List<String> texts = new ArrayList<>(List.of(item.explanation(), item.studyExperiment()));
            texts.addAll(item.aiLearningUses());
            texts.addAll(item.independentCapability());
            Optional<Located> found = namesAnother("moduleLearningInsights", learning.get(item.moduleId().strip()),
                    texts, titles);
            if (found.isPresent()) {
                return found;
            }
        }
        Map<String, UniversityPaidNarrativeContext.GuidanceModule> own = byId(context.ownCapabilityModules());
        for (HumanCapabilityInsight item : narrative.humanCapabilityInsights()) {
            Optional<Located> found = namesAnother("humanCapabilityInsights", own.get(item.moduleId().strip()),
                    List.of(item.explanation(), item.independentPractice()), titles);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Located> namesAnother(String section, UniversityPaidNarrativeContext.GuidanceModule module,
            List<String> texts, List<String> titles) {
        if (module == null) {
            return Optional.empty();
        }
        String paired = module.module();
        for (String text : texts) {
            String rest = text.replaceAll("(?i)" + Pattern.quote(paired), " ");
            for (String title : titles) {
                if (title.equalsIgnoreCase(paired) || paired.toLowerCase(Locale.ROOT).contains(
                        title.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                // Case-sensitive: a module is named by its title as written, so "ethics" in prose is never "Ethics".
                if (Pattern.compile("\\b" + Pattern.quote(title) + "\\b").matcher(rest).find()) {
                    return Optional.of(new Located(section + "[" + module.id() + "] is bound to \"" + paired
                            + "\" but explicitly describes \"" + title + "\"", text.strip()));
                }
            }
        }
        return Optional.empty();
    }

    private static String field(String section, String id, String field) {
        return section + "[" + id.strip() + "]." + field;
    }

    private static <T> List<Located> one(List<T> items, String section, Function<T, String> id, String field,
            Function<T, String> text) {
        return items.stream().map(item -> new Located(field(section, id.apply(item), field), text.apply(item))).toList();
    }

    private static <T> List<Located> each(List<T> items, String section, Function<T, String> id, String field,
            Function<T, List<String>> texts) {
        List<Located> located = new ArrayList<>();
        for (T item : items) {
            List<String> values = texts.apply(item);
            for (int index = 0; index < values.size(); index++) {
                located.add(new Located(field(section, id.apply(item), field + "[" + index + "]"), values.get(index)));
            }
        }
        return located;
    }

    /** Whether every content word, beyond the module's and the course's own titles, is generic study vocabulary. */
    private static boolean onlyGenericWords(String text, String title, UniversityPaidNarrativeContext context) {
        Set<String> own = new HashSet<>();
        for (String name : List.of(title == null ? "" : title, context.courseTitle())) {
            own.addAll(List.of(normalised(name).split(" ")));
        }
        List<String> content = Stream.of(normalised(text).split(" "))
                .filter(word -> word.length() > 3 && !own.contains(word)).toList();
        return !content.isEmpty() && GENERIC_STUDY_WORDS.containsAll(content);
    }

    private static Map<String, UniversityPaidNarrativeContext.GuidanceModule> byId(
            List<UniversityPaidNarrativeContext.GuidanceModule> modules) {
        return modules.stream().collect(Collectors.toMap(UniversityPaidNarrativeContext.GuidanceModule::id,
                Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static Optional<GenericFinding> repeated(List<Located> values) {
        Map<String, Located> seen = new LinkedHashMap<>();
        for (Located value : values) {
            Located earlier = seen.putIfAbsent(normalised(value.text()), value);
            if (earlier != null) {
                return Optional.of(new GenericFinding(value.location(), "repeats " + earlier.location() + " word for word",
                        value.text().strip(), earlier.text()));
            }
        }
        return Optional.empty();
    }

    /** Content words of a module's uses, without its own title, so naming the module never makes uses differ. */
    private static Set<String> words(String text, String title) {
        Set<String> own = title == null ? Set.of() : Set.of(normalised(title).split(" "));
        return Stream.of(normalised(text).split(" ")).filter(word -> word.length() > 3 && !own.contains(word))
                .collect(Collectors.toSet());
    }

    /** The share of the smaller set's words that the other set also has. */
    private static double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / Math.min(a.size(), b.size());
    }

    private static String normalised(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static List<String> withoutTitles(List<String> texts, UniversityPaidNarrativeContext context) {
        List<String> titles = new ArrayList<>();
        titles.add(context.courseTitle());
        context.modules().forEach(module -> titles.add(module.title()));
        titles.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return texts.stream().map(text -> {
            String stripped = text;
            for (String title : titles) {
                if (!title.isBlank()) {
                    stripped = stripped.replaceAll("(?i)" + Pattern.quote(title), "this module");
                }
            }
            return stripped;
        }).toList();
    }

    private static String sentence(Pattern pattern, List<String> prose) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text)) {
                if (pattern.matcher(sentence).find()) {
                    return sentence.strip();
                }
            }
        }
        return null;
    }
}
