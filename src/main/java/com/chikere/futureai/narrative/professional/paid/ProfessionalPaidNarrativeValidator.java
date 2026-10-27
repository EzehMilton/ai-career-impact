package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;
import com.chikere.futureai.trace.TraceStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@value ProfessionalPaidNarrativeVersions#V1} validation contract. The narrative explains frozen deterministic
 * findings, so it must keep every identifier in order and add, remove, reclassify, rerank or recommend nothing. The
 * language rules carry over the lessons of the role-level narrative v7; they are kept here, versioned with this
 * contract, so a later change to v7 never silently changes how a paid narrative is judged.
 *
 * <p>Quoted responsibility texts are the customer's own words and are removed before the language rules run, so a
 * responsibility such as "Follow safety regulations" never trips a rule by being quoted.</p>
 */
public final class ProfessionalPaidNarrativeValidator {

    public static final String PRESENT = "PAID_NARRATIVE_PRESENT";
    public static final String WELL_FORMED = "PAID_WELL_FORMED_STRUCTURED_OUTPUT";
    public static final String IDS = "PAID_IDS_MATCH_DETERMINISTIC_ORDER";
    public static final String NUMERIC = "NO_NUMERIC_SCORES_OR_PERCENTAGES";
    public static final String JOB_LOSS = "NO_JOB_LOSS_OR_SECURITY_INFERENCE";
    public static final String CAPABILITY = "NO_CATEGORICAL_AI_CAPABILITY_CLAIMS";
    public static final String TASK_AUTOMATION = "NO_UNSUPPORTED_TASK_AUTOMATION_CLAIMS";
    public static final String FUTURE = "NO_UNSUPPORTED_FUTURE_PREDICTIONS";
    public static final String OCCUPATION = "NO_OCCUPATION_WIDE_CLAIMS_OR_STEREOTYPES";
    public static final String RECLASSIFICATION = "NO_RESPONSIBILITY_RECLASSIFICATION";
    public static final String RANKING = "NO_RANKING_CHANGES";
    public static final String NEW_RECOMMENDATIONS = "NO_NEW_RECOMMENDATIONS";
    public static final String DELEGATION = "NO_UNSAFE_DELEGATION";
    public static final String PROCEDURE = "NO_UNSAFE_PROCEDURE_DEVIATION";
    public static final String VENDORS = "NO_NAMED_AI_VENDORS_OR_TOOLS";
    public static final String INTERNALS = "NO_INTERNAL_IDENTIFIERS_OR_CALIBRATION_LANGUAGE";
    public static final String BAND_WORDS = "NO_ENUM_STYLE_BAND_WORDS";
    public static final String SPELLING = "BRITISH_ENGLISH_SPELLING";
    public static final String SECOND_PERSON = "SECOND_PERSON";
    public static final String TONE = "RESTRAINED_TONE";

    static final List<String> RULES = List.of(PRESENT, WELL_FORMED, IDS, NUMERIC, JOB_LOSS, CAPABILITY,
            TASK_AUTOMATION, FUTURE, OCCUPATION, RECLASSIFICATION, RANKING, NEW_RECOMMENDATIONS, DELEGATION, PROCEDURE,
            VENDORS, INTERNALS, BAND_WORDS, SPELLING, SECOND_PERSON, TONE);

    /** Rules the single repair attempt may correct. A missing narrative is a provider failure, not prose to fix. */
    public static final Set<String> REPAIRABLE = Set.copyOf(RULES.subList(1, RULES.size()));

    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|per\\s?cent\\b|/\\s*10\\b|out\\s+of\\s+(?:10|ten)\\b|points?\\b)"
                    + "|\\b\\d+\\.\\d+\\b|\\bscor(?:e|ed|es|ing)\\s+(?:of\\s+)?\\d");

    private static final List<Pattern> JOB_LOSS_CLAIMS = patterns(
            "\\b(?:risk|chance|likelihood|probability|odds|danger)\\s+(?:of|that)\\s+(?:you\\s+)?(?:will\\s+)?(?:losing"
                    + "|lose|job\\s+loss|redundanc\\w*|being\\s+(?:replaced|made\\s+redundant|automated)|replacement"
                    + "|automation)\\b",
            "\\b(?:lose|losing)\\s+(?:your|their|the)\\s+(?:job|role|position|employment)\\b",
            "\\bjob\\s+(?:security|safety|stability|loss)\\b",
            "\\b(?:your|the|this)\\s+(?:job|role|position|work|career)\\s+(?:is|are|will\\s+be|remains?|seems?|appears?)\\s+"
                    + "(?:\\w+\\s+)?(?:safe|secure|protected|at\\s+risk|threatened|in\\s+danger)\\b",
            "\\bat\\s+(?:a\\s+)?(?:low|high|little|no|some|greater|lower)?\\s*risk\\s+of\\s+(?:automation|redundancy"
                    + "|replacement|being\\s+replaced|job\\s+loss)\\b",
            "\\bautomation\\s+risk\\b", "\\breplacement\\s+(?:risk|probability)\\b",
            "\\b(?:salary|salaries|pay\\s+rise|earnings|promotion\\s+prospects)\\b");

    private static final List<Pattern> CAPABILITY_CLAIMS = patterns(
            "\\b(?:AI|artificial\\s+intelligence|machines?|technology|automation|computers?|algorithms?)\\s+"
                    + "(?:(?:currently|still|simply|really|yet|today|ever|fully|easily|truly)\\s+)?"
                    + "(?:cannot|can\\s+not|can't|is\\s+unable\\s+to|are\\s+unable\\s+to|will\\s+never|could\\s+never"
                    + "|can\\s+never|is\\s+incapable\\s+of|are\\s+incapable\\s+of|lacks?\\s+the\\s+(?:ability|capacity))\\b",
            "\\b(?:cannot|can\\s+not|can't)\\s+(?:\\w+\\s+){0,2}?(?:be\\s+)?(?:replicated|replaced|automated|reproduced"
                    + "|substituted|imitated|done\\s+by\\s+(?:AI|machines?))\\b",
            "\\b(?:only|uniquely)\\s+(?:a\\s+)?(?:humans?|people|a\\s+person|human\\s+beings)\\s+(?:can|could)\\b",
            "\\bimpossible\\s+(?:for\\s+(?:AI|machines?|technology)|to\\s+(?:automate|replicate|replace))\\b",
            "\\bbeyond\\s+(?:the\\s+)?(?:reach|capabilit\\w*|abilit\\w*)\\s+of\\s+(?:AI|machines?|technology)\\b",
            "\\birreplaceable\\b", "\\bAI[-\\s]proof\\b",
            "\\bAI\\s+(?:can|could|will|would|may|might)\\s+(?:\\w+\\s+)?replace\\b",
            "\\breplace(?:s|d)?\\s+(?:you|people|humans?|workers|staff|professionals)\\b");

    private static final List<Pattern> TASK_AUTOMATION_CLAIMS = patterns(
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+\\s+)?(?:automated|handled|performed|done"
                    + "|replaced|taken\\s+over|carried\\s+out|completed|managed)\\s+(?:\\w+\\s+){0,3}?(?:by|with|using)\\s+"
                    + "(?:AI|artificial\\s+intelligence|machines?|automation|software|algorithms?|automated)\\b",
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+ly\\s+)?automated\\b",
            "\\b(?:is|are|seems?|appears?)\\s+(?:\\w+\\s+)?automatable\\b",
            "\\bautomatable\\s+tasks?\\b", "\\btasks?\\s+to\\s+replace\\b",
            "(?<!what\\s)\\bAI(?:\\s+(?:systems?|tools?|models?|software))?\\s+(?:can|could|will|would)\\s+(?:\\w+\\s+)?"
                    + "(?:perform|handle|do|take\\s+over|complete|run|carry\\s+out|automate|manage)\\b",
            "\\bwork\\s+(?:that\\s+)?AI\\s+can\\s+take\\s+over\\b");

    private static final List<Pattern> FUTURE_CLAIMS = patterns(
            "\\b(?:likely|expected|set|bound|poised|going|predicted|projected|forecast|anticipated)\\s+to\\s+"
                    + "(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|increase|disappear"
                    + "|be\\s+(?:transformed|automated|reshaped|changed|disrupted|replaced))\\b",
            "\\bwill\\s+(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|disappear"
                    + "|be\\s+(?:transformed|reshaped|changed|disrupted|replaced|automated|eliminated))\\b",
            "\\bwill\\s+increasingly\\b", "\\bwill\\s+remain\\b", "\\bwill\\s+always\\b",
            "\\bwill\\s+continue\\s+to\\s+(?:be|require|need|rely|depend|play|matter|demand)\\b",
            "\\bunlikely\\s+to\\s+(?:be\\s+)?(?:replaced|automated|eliminated|disappear|change|affected|disrupted)\\b",
            "\\bsafe\\s+from\\b", "\\bfuture[-\\s]?proof\\w*", "\\bdoomed\\b", "\\bobsolete\\b",
            "\\b(?:protected|shielded|immune|insulated|secure)\\s+(?:from|against)\\s+(?:AI|artificial\\s+intelligence"
                    + "|automation|replacement|technolog\\w*)\\b",
            "\\bAI\\s+will\\s+(?:replace|take\\s+over|eliminate|transform)\\b", "\\bin\\s+the\\s+(?:coming|next)\\s+"
                    + "(?:years?|decade)\\b", "\\bby\\s+20\\d\\d\\b");

    private static final List<Pattern> STEREOTYPES = patterns(
            "\\b(?:people|professionals|workers|staff|those|everyone)\\s+in\\s+(?:this|your|the\\s+same)\\s+"
                    + "(?:role|profession|occupation|field|line\\s+of\\s+work|industry)\\s+(?:are|tend|usually|typically|often)\\b",
            "\\b(?:most|all|many|typical)\\s+(?:people|professionals|workers)\\s+(?:in|like)\\s+(?:you|your)\\b",
            "\\b(?:as\\s+a\\s+rule|by\\s+nature)\\b");

    /** Ranking talk the narrative could use to reorder fixed priorities. */
    private static final List<Pattern> RANKING_CLAIMS = patterns(
            "\\bre-?(?:rank|order|prioriti[sz])\\w*\\b",
            "\\b(?:more|less)\\s+(?:important|promising|suitable|valuable)\\s+than\\b",
            "\\b(?:should|could)\\s+come\\s+(?:first|before|ahead)\\b",
            "\\b(?:better|best)\\s+(?:to\\s+)?start\\s+with\\b(?!\\s+the\\s+pilot)",
            "\\binstead\\s+of\\s+(?:the|this|that)\\s+(?:opportunity|pilot|priority|action|skill)\\b",
            "\\b(?:lowest|least)\\s+(?:priority|important)\\b");

    /** New recommendations the deterministic guidance never makes. */
    private static final List<Pattern> NEW_RECOMMENDATION_CLAIMS = patterns(
            "\\b(?:courses?|certificat\\w*|qualifications?|bootcamps?|diplomas?|webinars?|tutorials?|MOOCs?)\\b",
            "\\b(?:enrol|enroll|sign\\s+up|subscribe|purchase|buy|download)\\w*\\b",
            // Installing software or a tool. Not the noun "installation" or installing physical work, which
            // describes the customer's own responsibilities (an electrician's installation work).
            "\\binstall(?:s|ed|ing)?\\s+(?:(?:a|an|the|some|new|any|this|that|your)\\s+)?(?:[\\w-]+\\s+){0,2}?"
            + "(?:apps?|applications?|software|tools?|plug-?ins?|extensions?|add-?ons?|programs?|assistants?|AI)\\b",
            "\\b(?:hire|recruit)\\s+(?:a|an|someone)\\b",
            "\\b(?:change|switch|leave)\\s+(?:your\\s+)?(?:career|careers|job|jobs|profession|role)\\b",
            "\\bretrain\\w*\\b", "\\blearn\\s+(?:AI|to\\s+code|coding|programming|prompt\\s+engineering)\\b",
            "\\bprompt\\s+engineering\\b");

    /** Handing judgement, decisions or responsibility to AI, or removing human review. */
    private static final List<Pattern> DELEGATION_CLAIMS = patterns(
            "\\b(?:let|allow|have)\\s+(?:the\\s+)?AI\\s+(?:to\\s+)?(?:decide|make|take|approve|sign|choose|judge|diagnose)\\w*\\b",
            "\\b(?:leave|hand|pass|delegate|offload|outsource|hand\\s+over|hand\\s+off)\\s+(?:\\w+\\s+){0,4}?to\\s+"
                    + "(?:the\\s+)?(?:AI|an?\\s+AI|AI\\s+tools?|automation)\\b",
            "\\b(?:rely|depend)\\s+(?:entirely|solely|fully|wholly|completely)\\s+on\\s+(?:the\\s+)?AI\\b",
            "\\bwithout\\s+(?:any\\s+)?(?:human\\s+)?(?:review|checking|checks|oversight|sign-?off)\\b",
            "\\b(?:skip|drop|remove|reduce|relax)\\w*\\s+(?:\\w+\\s+){0,2}?(?:human\\s+)?(?:review|checking|checks|oversight)\\b",
            "\\bAI\\s+(?:should|can|could|will)\\s+(?:\\w+\\s+)?(?:make|take)\\s+(?:the\\s+)?(?:\\w+\\s+)?decisions?\\b",
            "\\btrust\\s+(?:the\\s+)?AI(?:'s)?\\s+(?:output|answer|judgement|decision)s?\\b");

    private static final Pattern DEVIATION = Pattern.compile("(?i)\\b(?:ignor\\w*|bypass\\w*|skip\\w*|loosen\\w*"
            + "|relax\\w*|deviat\\w*|depart\\w*\\s+from|work(?:ing)?\\s+around|circumvent\\w*|overrid\\w*"
            + "|disregard\\w*|shortcut\\w*|cut(?:ting)?\\s+corners|abandon\\w*|bend(?:ing)?|waiv\\w*|set(?:ting)?\\s+aside)\\b");
    private static final Pattern REGULATED = Pattern.compile("(?i)\\b(?:procedures?|protocols?|checks?|checklists?"
            + "|polic(?:y|ies)|guidelines?|regulations?|regulatory\\s+requirements?|legal\\s+(?:obligations?"
            + "|requirements?)|professional\\s+(?:standards?|obligations?|duties)|security\\s+controls?|controls?"
            + "|safeguards?|confidentiality|privacy|data\\s+protection|consent|compliance|approvals?)\\b");
    private static final Pattern SAFETY_NEGATION = Pattern.compile("(?i)\\b(?:never|not|no|without|avoid\\w*"
            + "|rather\\s+than|instead\\s+of)\\b|n't\\b");

    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(?:ChatGPT|GPT-?\\d\\w*|OpenAI|Copilot|Gemini|Bard"
            + "|Claude|Anthropic|Llama|Mistral|Perplexity|Grammarly|Jasper|Midjourney|DALL-?E|Notion\\s+AI|Otter"
            + "|Microsoft|Google|Amazon|AWS|Azure|IBM\\s+Watson|Salesforce|Einstein)\\b");

    private static final List<Pattern> INTERNAL_LANGUAGE = List.of(
            Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b"),
            Pattern.compile("(?i)\\b(?:opportunity|human-value|skill|action)-\\d+-[a-z0-9-]+\\b"),
            Pattern.compile("(?i)\\b(?:probabilit(?:y|ies)|distributions?|provider\\s+confidence|confidence\\s+(?:score"
                    + "|level|interval)|calibrat\\w*|threshold\\w*|described\\s+clearly|close\\s+call|Jev|TypeSafe"
                    + "|language\\s+model|LLM|rule\\s+(?:id|identifier))\\b"));

    private static final Pattern ENUM_BAND_WORD = Pattern.compile("\\b(?:LOW|MODERATE|HIGH)\\b");
    private static final Pattern AMERICAN_SPELLING = Pattern.compile("(?i)\\b(?:judgments?|prioritiz\\w*"
            + "|personaliz\\w*|organiz\\w*|emphasiz\\w*|analyz\\w*|recogniz\\w*|behaviors?|optimiz\\w*|specializ\\w*"
            + "|utiliz\\w*|minimiz\\w*|maximiz\\w*|standardiz\\w*|summariz\\w*|familiariz\\w*|center(?:s|ed)?"
            + "|color\\w*|favor\\w*|labor)\\b");
    private static final Pattern SECOND_PERSON_WORDS = Pattern.compile("(?i)\\b(?:you|your|yours|yourself)\\b");
    /**
     * Writing about the customer rather than to them. A hyphenated compound ("the customer-facing parts of your work")
     * describes the work, not a person, so the noun must not continue with a hyphen.
     */
    private static final Pattern THIRD_PERSON = Pattern.compile("(?i)\\b(?:the\\s+(?:user|customer|client|respondent"
            + "|individual|person\\s+who\\s+completed|employee|worker)(?![\\w-])"
            + "|this\\s+(?:person|individual|professional)\\b|he\\s+or\\s+she\\b)");
    /**
     * A clause telling a named third person what to do: "A customer-service adviser must...". Not "they", which a
     * regex cannot resolve and usually refers to things ("AI drafts help, but they should still be checked by you").
     */
    private static final String THIRD_PERSON_SUBJECT = "(?:(?:a|an|the)\\s+(?:[\\w-]+\\s+){0,3}?(?:worker|employee"
            + "|professional|practitioner|%s))";
    private static final String THIRD_PERSON_INSTRUCTION = "(?i)(?:^\\s*|[,;:]\\s*(?:(?:and|but|so|while|yet)\\s+)?)"
            + "%s\\s+(?:should|must|ought\\s+to|needs?\\s+to|has\\s+to|have\\s+to)\\b";
    private static final Pattern HYPE = Pattern.compile("(?i)!|\\b(?:amazing|exciting|revolutionar\\w*|game[-\\s]?chang\\w*"
            + "|dramatic(?:ally)?|massive(?:ly)?|huge(?:ly)?|incredibl[ey]|unprecedented|skyrocket\\w*|supercharg\\w*"
            + "|transformative|disrupt\\w*|alarming|terrif\\w*|guarantee\\w*|definitely|certainly|undoubtedly"
            + "|your\\s+job\\s+is\\s+at\\s+risk)\\b");

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile("(?i);|\\bbut\\b|\\bhowever\\b");
    private static final Pattern DISCLAIMER = Pattern.compile("(?i)"
            + "\\b(?:does|do|did|is|are|was|were|should|can|could|would|will)\\s*(?:not|n't)\\s+(?:\\w+\\s+){0,3}?"
            + "(?:predict|show|mean|imply|indicate|suggest|claim|say|tell|assess|measure|forecast|establish|prove"
            + "|determine|guarantee|describe|reflect|evaluate|judge|consider)\\w*"
            + "|\\bnot\\s+(?:a|an)\\s+(?:prediction|probability|forecast|guarantee|measure|assessment|statement"
            + "|judgement|verdict|claim)\\b"
            + "|\\bwithout\\s+(?:predicting|implying|suggesting|claiming)\\b"
            + "|\\bno\\s+(?:prediction|claim|forecast|guarantee)\\b");

    private final AssessmentTrace trace;

    public ProfessionalPaidNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    public ProfessionalPaidNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    /** Returns the narrative unchanged if every rule passes; otherwise throws the first rule's violation. */
    public ProfessionalPaidNarrative validate(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.PROFESSIONAL, RULES,
                TraceStage.PAID_NARRATIVE_VALIDATION);
        checks.check(PRESENT, narrative == null, () -> null, "the provider returned no paid narrative");
        checks.check(WELL_FORMED, malformed(narrative).isPresent(), () -> malformed(narrative).orElse(null),
                "a required paid-narrative field is missing or empty");
        checks.check(IDS, idMismatch(narrative, context).isPresent(), () -> idMismatch(narrative, context).orElse(null),
                "every deterministic identifier must appear exactly once, in the deterministic order, and nothing else");

        List<String> prose = prose(narrative, context);
        claim(checks, NUMERIC, prose, List.of(NUMERIC_CLAIM), false,
                "the paid narrative must not state scores, percentages or other numbers");
        claim(checks, JOB_LOSS, prose, JOB_LOSS_CLAIMS, true,
                "the paid narrative must not infer job loss, job security, salaries or career outcomes");
        claim(checks, CAPABILITY, prose, CAPABILITY_CLAIMS, true,
                "the paid narrative must not make categorical claims about what AI can or cannot do or replace");
        claim(checks, TASK_AUTOMATION, prose, TASK_AUTOMATION_CLAIMS, true,
                "the paid narrative must describe assistance, not tasks AI can perform, automate or take over");
        claim(checks, FUTURE, prose, FUTURE_CLAIMS, true,
                "the paid narrative must not predict the future of the work, role or profession");
        List<Pattern> occupation = new ArrayList<>(STEREOTYPES);
        occupationWideClaim(context.profession()).ifPresent(occupation::add);
        claim(checks, OCCUPATION, prose, occupation, false,
                "the paid narrative must describe the work you described, not everyone in the occupation");
        Optional<String> reclassified = reclassification(narrative, context);
        checks.check(RECLASSIFICATION, reclassified.isPresent(), reclassified::orElseThrow,
                "an explanation must not give a responsibility a different category from the stored one");
        claim(checks, RANKING, prose, RANKING_CLAIMS, false,
                "the paid narrative must not reorder or reprioritise the deterministic findings");
        Optional<String> invented = newRecommendation(prose, context);
        checks.check(NEW_RECOMMENDATIONS, invented.isPresent(), invented::orElseThrow,
                "the paid narrative must not add courses, tools, career moves or skills that were not supplied");
        Optional<String> delegation = unnegated(prose, DELEGATION_CLAIMS);
        checks.check(DELEGATION, delegation.isPresent(), delegation::orElseThrow,
                "the paid narrative must keep human review, judgement and decisions with you");
        Optional<String> deviation = procedureDeviation(prose);
        checks.check(PROCEDURE, deviation.isPresent(), deviation::orElseThrow,
                "the paid narrative must never suggest setting aside a procedure, policy or obligation");
        claim(checks, VENDORS, prose, List.of(VENDOR), false,
                "the paid narrative must not name AI vendors, products or tools");
        claim(checks, INTERNALS, prose, INTERNAL_LANGUAGE, false,
                "the paid narrative must not expose identifiers, probabilities or calibration language");
        claim(checks, BAND_WORDS, prose, List.of(ENUM_BAND_WORD), false,
                "band words must be written in ordinary lower case inside sentences");
        claim(checks, SPELLING, prose, List.of(AMERICAN_SPELLING), false, "the paid narrative must use British English");
        Optional<String> person = secondPerson(narrative, prose, context.profession());
        checks.check(SECOND_PERSON, person.isPresent(), person::orElseThrow,
                "the paid narrative must address you directly, in the second person");
        claim(checks, TONE, prose, List.of(HYPE), false, "the paid narrative must keep a calm, restrained tone");
        checks.accepted();
        return narrative;
    }

    private static Optional<String> malformed(ProfessionalPaidNarrative narrative) {
        if (narrative == null) {
            return Optional.empty();
        }
        List<String> missing = new ArrayList<>();
        text("executiveSummary", narrative.executiveSummary(), missing);
        text("scoresTogether", narrative.scoresTogether(), missing);
        text("responsibilityPattern", narrative.responsibilityPattern(), missing);
        text("skillDevelopment", narrative.skillDevelopment(), missing);
        text("planIntroduction", narrative.planIntroduction(), missing);
        text("closingPerspective", narrative.closingPerspective(), missing);
        items("opportunityExplanations", narrative.opportunityExplanations(), missing);
        items("humanValueExplanations", narrative.humanValueExplanations(), missing);
        items("skillExplanations", narrative.skillExplanations(), missing);
        items("planActionNotes", narrative.planActionNotes(), missing);
        return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing));
    }

    private static void text(String name, String value, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    private static void items(String name, List<ItemExplanation> values, List<String> missing) {
        if (values == null) {
            missing.add(name);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            ItemExplanation item = values.get(index);
            if (item == null || item.id() == null || item.id().isBlank() || item.explanation() == null
                    || item.explanation().isBlank()) {
                missing.add(name + "[" + index + "]");
            }
        }
    }

    private static Optional<String> idMismatch(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        return Stream.of(
                        compare("opportunityExplanations", narrative.opportunityExplanations(), context.opportunityIds()),
                        compare("humanValueExplanations", narrative.humanValueExplanations(), context.humanValueIds()),
                        compare("skillExplanations", narrative.skillExplanations(), context.skillIds()),
                        compare("planActionNotes", narrative.planActionNotes(), context.planActionIds()))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> compare(String field, List<ItemExplanation> items, List<String> expected) {
        List<String> actual = items.stream().map(item -> item.id().strip()).toList();
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

    /** Every prose field, with the customer's own responsibility texts removed. */
    private static List<String> prose(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        List<String> texts = new ArrayList<>(List.of(narrative.executiveSummary(), narrative.scoresTogether(),
                narrative.responsibilityPattern(), narrative.skillDevelopment(), narrative.planIntroduction(),
                narrative.closingPerspective()));
        Stream.of(narrative.opportunityExplanations(), narrative.humanValueExplanations(), narrative.skillExplanations(),
                narrative.planActionNotes()).flatMap(Collection::stream).map(ItemExplanation::explanation)
                .forEach(texts::add);
        List<String> userTexts = context.responsibilityTexts().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        return texts.stream().map(text -> {
            String cleaned = text;
            for (String user : userTexts) {
                cleaned = cleaned.replaceAll("(?i)" + Pattern.quote(user), "this responsibility");
            }
            return cleaned;
        }).toList();
    }

    private static Optional<String> reclassification(ProfessionalPaidNarrative narrative,
            ProfessionalPaidNarrativeContext context) {
        for (ItemExplanation item : Stream.concat(narrative.opportunityExplanations().stream(),
                narrative.humanValueExplanations().stream()).toList()) {
            ResponsibilityCategory stored = context.categoryById().get(item.id().strip());
            String lower = item.explanation().toLowerCase(Locale.ROOT);
            for (ResponsibilityCategory other : ResponsibilityCategory.values()) {
                // "AI may assist with parts" is also ordinary advice, so only its quoted, label-like use counts.
                String label = other.label().toLowerCase(Locale.ROOT);
                String named = other == ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT ? "\"" + label : label;
                if (other != stored && lower.contains(named)) {
                    return Optional.of(item.explanation());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> newRecommendation(List<String> prose, ProfessionalPaidNarrativeContext context) {
        Optional<String> invented = firstUndisclaimed(prose, NEW_RECOMMENDATION_CLAIMS, false);
        if (invented.isPresent()) {
            return invented;
        }
        for (String text : prose) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String label : context.unselectedSkillLabels()) {
                if (lower.contains(label.toLowerCase(Locale.ROOT))) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    /** A claim not negated earlier in its sentence: "never use it without review" is a safeguard, not a delegation. */
    private static Optional<String> unnegated(List<String> prose, List<Pattern> claims) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                for (Pattern claim : claims) {
                    Matcher matcher = claim.matcher(sentence);
                    while (matcher.find()) {
                        if (!SAFETY_NEGATION.matcher(sentence.substring(0, matcher.start())).find()) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> procedureDeviation(List<String> prose) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                Matcher deviation = DEVIATION.matcher(sentence);
                while (deviation.find()) {
                    String before = sentence.substring(0, deviation.start());
                    if (REGULATED.matcher(sentence).find() && !SAFETY_NEGATION.matcher(before).find()) {
                        return Optional.of(sentence.strip());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The summary must address you, and no sentence may describe you in the third person. A sentence need not begin
     * with "you": "These skills support your work" is second person.
     */
    private static Optional<String> secondPerson(ProfessionalPaidNarrative narrative, List<String> prose,
            String profession) {
        if (!SECOND_PERSON_WORDS.matcher(narrative.executiveSummary()).find()) {
            return Optional.of(narrative.executiveSummary());
        }
        String last = profession.strip().replaceAll(".*[\\s-]", "").replaceAll("[^A-Za-z]", "");
        String role = last.length() < 3 ? "worker" : Pattern.quote(last) + "s?";
        Pattern instruction = Pattern.compile(THIRD_PERSON_INSTRUCTION.formatted(THIRD_PERSON_SUBJECT.formatted(role)));
        return firstUndisclaimed(prose, List.of(THIRD_PERSON, instruction), false);
    }

    /** A claim about everyone with the job title, such as "nurses are" or "solicitors will". */
    private static Optional<Pattern> occupationWideClaim(String profession) {
        String last = profession.strip().replaceAll(".*\\s", "").replaceAll("[^A-Za-z-]", "");
        if (last.length() < 3) {
            return Optional.empty();
        }
        String plural = last.endsWith("s") ? last : last + "s";
        return Optional.of(Pattern.compile("(?i)\\b(?:all\\s+|most\\s+|many\\s+|typical\\s+)?" + Pattern.quote(plural)
                + "\\s+(?:are|tend|will|must|always|usually|typically|often|cannot|can't|need)\\b"));
    }

    private void claim(NarrativeRuleChecks checks, String rule, List<String> text, List<Pattern> claims,
            boolean disclaimable, String rejection) {
        Optional<String> found = firstUndisclaimed(text, claims, disclaimable);
        checks.check(rule, found.isPresent(), found::orElseThrow, rejection);
    }

    /** The first sentence that makes one of the claims; with {@code disclaimable}, an explicit denial lets it pass. */
    private static Optional<String> firstUndisclaimed(List<String> texts, List<Pattern> claims, boolean disclaimable) {
        for (String text : texts) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                for (Pattern claim : claims) {
                    Matcher matcher = claim.matcher(sentence);
                    while (matcher.find()) {
                        if (!disclaimable || !disclaimed(sentence.substring(0, matcher.end()))) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean disclaimed(String before) {
        Matcher breaks = CLAUSE_BREAK.matcher(before);
        int clauseStart = 0;
        while (breaks.find()) {
            clauseStart = breaks.end();
        }
        return DISCLAIMER.matcher(before.substring(clauseStart)).find();
    }

    private static List<Pattern> patterns(String... regexes) {
        return Stream.of(regexes).map(regex -> Pattern.compile("(?i)" + regex)).toList();
    }
}
