package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Deterministic guardrail applied after typed provider mapping: the safety boundary between generated prose and
 * what a visitor may read.
 *
 * <p>The prompt asks the model not to forecast, not to claim what AI can never do, not to infer job security,
 * not to credit the person with adaptability and to explain the factors Java selected. This class rejects a
 * narrative that does any of those anyway. Rejection never touches the saved assessment: the narrative is stored
 * as failed and the visitor sees the existing fallback. Claims are matched sentence by sentence and an explicit
 * disclaimer of a claim is allowed (see {@link NarrativeClaimMatcher}).</p>
 */
public final class ProfessionalNarrativeValidator {

    static final String NUMERIC = "NO_NUMERIC_SCORES_OR_PERCENTAGES";
    static final String FUTURE = "NO_UNSUPPORTED_FUTURE_PREDICTIONS";
    static final String CAPABILITY = "NO_CATEGORICAL_AI_CAPABILITY_CLAIMS";
    static final String TASK_AUTOMATION = "NO_UNSUPPORTED_TASK_AUTOMATION_CLAIMS";
    static final String JOB_LOSS = "NO_JOB_LOSS_OR_SECURITY_INFERENCE";
    static final String ADAPTABILITY = "ROLE_ADAPTABILITY_IS_ROLE_LEVEL";
    static final String GROUNDED = "EXPOSURE_EXPLANATION_GROUNDED_IN_FACTORS";
    static final String DIRECTIONS = "EXPOSURE_FACTOR_DIRECTIONS_MATCH";
    static final String LEVELS = "EXPOSURE_FACTOR_LEVELS_MATCH";
    static final String OCCUPATION = "NO_OCCUPATION_WIDE_CLAIMS";
    static final String BAND_WORDS = "NO_ENUM_STYLE_BAND_WORDS";
    static final String SPELLING = "BRITISH_ENGLISH_SPELLING";
    static final String RESPONSIBILITY_SCORING = "NO_RESPONSIBILITY_LEVEL_SCORING";
    static final String DOMINANT = "EXPOSURE_EXPLANATION_LEADS_WITH_DOMINANT_FACTOR";
    static final String JARGON = "NO_INTERNAL_DIMENSION_JARGON";
    static final String UNSAFE_PROCEDURE = "NO_UNSAFE_PROCEDURE_DEVIATION";
    static final String NO_MATERIAL_FACTORS = "NO_FACTOR_ATTRIBUTION_WITHOUT_MATERIAL_FACTORS";

    private static final List<String> TEXT_RULES = List.of("NARRATIVE_PRESENT", "WELL_FORMED_STRUCTURED_OUTPUT",
            NUMERIC, FUTURE, CAPABILITY, TASK_AUTOMATION, JOB_LOSS, ADAPTABILITY, JARGON, UNSAFE_PROCEDURE, BAND_WORDS, SPELLING);
    private static final List<String> CONTEXT_RULES = Stream.concat(TEXT_RULES.stream(),
            Stream.of(GROUNDED, DOMINANT, DIRECTIONS, LEVELS, OCCUPATION, RESPONSIBILITY_SCORING,
                    NO_MATERIAL_FACTORS)).toList();

    // No trailing \\b: a percent sign is not a word character, so "92%" would otherwise slip through.
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|/\\s*10\\b|out\\s+of\\s+10\\b)");

    /**
     * Forecasts about the work, the role or the profession in either direction: reassurances that it will stay the
     * same and predictions that it will change. The assessment describes the work as submitted today; it does not
     * predict the future direction of the occupation or role.
     */
    private static final List<Pattern> FUTURE_CLAIMS = patterns(
            "\\b(?:likely|expected|set|bound|poised|going|predicted|projected|forecast|anticipated)\\s+to\\s+"
                    + "(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|increase|depend|rely"
                    + "|be\\s+(?:transformed|automated|reshaped|changed|disrupted|replaced))\\b",
            "\\bwill\\s+(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|depend|rely"
                    + "|be\\s+(?:transformed|reshaped|changed|disrupted))\\b",
            "\\bwill\\s+increasingly\\b",
            "\\bnot\\s+(?:expected|likely|set|going|anticipated)\\s+to\\s+(?:\\w+\\s+)?(?:alter|change|affect|transform"
                    + "|disrupt|replace|reshape)\\b",
            "\\b(?:will\\s+not|won't)\\s+(?:\\w+\\s+)?(?:change|alter|affect|transform|disrupt|be\\s+(?:replaced|automated"
                    + "|affected|disrupted|eliminated))\\b",
            "\\bwill\\s+remain\\b",
            "\\bwill\\s+continue\\s+to\\s+(?:be|require|need|rely|depend|play|matter|demand)\\b",
            "\\bwill\\s+always\\b",
            "\\bunlikely\\s+to\\s+(?:be\\s+)?(?:replaced|automated|eliminated|disappear|change|affected|disrupted)\\b",
            "\\bsafe\\s+from\\b",
            "\\bfuture[-\\s]?proof\\w*",
            "\\b(?:protected|shielded|immune|insulated|secure)\\s+(?:from|against)\\s+(?:AI|artificial\\s+intelligence"
                    + "|automation|replacement|technolog\\w*)\\b",
            "\\bwill\\s+(?:disappear|become\\s+obsolete|cease\\s+to\\s+exist|be\\s+(?:replaced|automated|eliminated))\\b",
            "\\bAI\\s+will\\s+(?:replace|take\\s+over|eliminate)\\b");

    /** Categorical claims about what AI or technology can never do. */
    private static final List<Pattern> CAPABILITY_CLAIMS = patterns(
            "\\b(?:AI|artificial\\s+intelligence|machines?|technology|automation|computers?|algorithms?)\\s+"
                    + "(?:cannot|can\\s+not|can't|is\\s+unable\\s+to|are\\s+unable\\s+to|will\\s+never|could\\s+never"
                    + "|can\\s+never|is\\s+incapable\\s+of|are\\s+incapable\\s+of|lacks?\\s+the\\s+(?:ability|capacity))\\b",
            "\\b(?:cannot|can\\s+not|can't)\\s+(?:\\w+\\s+){0,2}?(?:be\\s+)?(?:replicated|replaced|automated|reproduced"
                    + "|substituted|imitated|done\\s+by\\s+(?:AI|machines?))\\b",
            "\\b(?:only|uniquely)\\s+(?:a\\s+)?(?:humans?|people|a\\s+person|human\\s+beings)\\s+(?:can|could)\\b",
            "\\bimpossible\\s+(?:for\\s+(?:AI|machines?|technology)|to\\s+(?:automate|replicate|replace))\\b",
            "\\bbeyond\\s+(?:the\\s+)?(?:reach|capabilit\\w*|abilit\\w*)\\s+of\\s+(?:AI|machines?|technology)\\b",
            "\\birreplaceable\\b");

    /**
     * Claims that tasks or the work can be automated, performed, handled or replaced by AI. The assessment is
     * role-level: it describes characteristics of the work and scope for assistance, and has not established that any
     * task is automatable, however high the exposure. "AI may assist…" and "scope for assistance" are untouched.
     */
    private static final List<Pattern> TASK_AUTOMATION_CLAIMS = patterns(
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+\\s+)?(?:automated|handled|performed|done"
                    + "|replaced|taken\\s+over|carried\\s+out|completed|managed)\\s+(?:\\w+\\s+){0,3}?(?:by|with|using)\\s+"
                    + "(?:AI|artificial\\s+intelligence|machines?|automation|software|algorithms?|automated)\\b",
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+ly\\s+)?automated\\b",
            "\\b(?:is|are|seems?|appears?)\\s+(?:\\w+\\s+)?automatable\\b",
            // Not when describing AI capability in general, as the measure's definition does ("what AI can do").
            "(?<!what\\s)(?<!characteristics\\s)(?<!things\\s)(?<!activities\\s)"
                    + "\\bAI(?:\\s+(?:systems?|tools?|models?|software))?\\s+(?:can|could|will|would)\\s+(?:\\w+\\s+)?(?:perform"
                    + "|handle|do|take\\s+over|complete|replace|run|carry\\s+out|automate|manage)\\b");

    /** AI Exposure turned into risk of losing the job, replacement or job security. */
    private static final List<Pattern> JOB_LOSS_CLAIMS = patterns(
            "\\b(?:risk|chance|likelihood|probability|odds|danger)\\s+(?:of|that)\\s+(?:you\\s+)?(?:will\\s+)?(?:losing"
                    + "|lose|job\\s+loss|redundanc\\w*|being\\s+(?:replaced|made\\s+redundant|automated)|replacement"
                    + "|automation)\\b",
            "\\b(?:lose|losing)\\s+(?:your|their|the)\\s+(?:job|role|position|employment)\\b",
            "\\bjob\\s+(?:security|safety|stability)\\b",
            "\\b(?:your|the|this)\\s+(?:job|role|position|work)\\s+(?:is|are|will\\s+be|remains?|seems?|appears?)\\s+"
                    + "(?:\\w+\\s+)?(?:safe|secure|protected|at\\s+risk|threatened|in\\s+danger)\\b",
            "\\bat\\s+(?:a\\s+)?(?:low|high|little|no|some|greater|lower)?\\s*risk\\s+of\\s+(?:automation|redundancy"
                    + "|replacement|being\\s+replaced|job\\s+loss)\\b",
            "\\b(?:your|the|this)\\s+(?:job|role|position)\\s+will\\s+be\\s+(?:replaced|automated|lost|eliminated)\\b");

    /** Personal traits credited to the person (or someone else) instead of the structure of the role. */
    private static final List<Pattern> PERSONAL_ADAPTABILITY_CLAIMS = patterns(
            "\\b(?:your|their|his|her|the\\s+(?!role\\b|work\\b|job\\b|position\\b|structure\\b|occupation\\b"
                    + "|assessment\\b|organisation\\b|organization\\b|team\\b)\\w+['’]s)\\s+(?:own\\s+|personal\\s+)?"
                    + "(?:ability|abilities|capacity|willingness|readiness|openness|eagerness|resilience|flexibility"
                    + "|adaptability|motivation|aptitude|capability|capabilities|talent|drive|attitude|mindset)\\b",
            "\\byou\\s+(?:are|seem|appear|have\\s+shown|have\\s+proven|tend\\s+to\\s+be)\\s+(?:\\w+\\s+)?(?:adaptable"
                    + "|flexible|resilient|motivated|open|willing|eager|able\\s+to\\s+adapt)\\b",
            "\\bwillingness\\s+to\\s+(?:learn|change|adapt|retrain|upskill|embrace)\\b",
            "\\bopenness\\s+to\\s+(?:new\\s+)?(?:technolog\\w*|change|AI)\\b",
            "\\b(?:people|professionals|workers|staff|those)\\s+in\\s+(?:this|the|your)\\s+role\\s+(?:are|tend\\s+to\\s+be)"
                    + "\\s+(?:\\w+\\s+)?(?:adaptable|flexible|resilient|motivated)\\b");

    /**
     * An assessed classification stated as belonging to whatever precedes it: an exposure score, band or rating,
     * being highly or moderately exposed, or being automatable. Only checked directly after a submitted
     * responsibility, because the assessment is role-level and never scores one.
     */
    private static final Pattern ASSESSED_CLASSIFICATION = Pattern.compile("(?i)^\\W*(?:\\w+\\s+){0,3}?(?:"
            + "(?:has|have|had|carries|carry|scores?|scored|gets|receives|shows|is|are|was|were|rates?|rated|sits?)"
            + "(?:\\s+\\w+){0,4}?\\s+(?:(?:low|moderate|high|highly|moderately|lightly|slightly|very|strongly|heavily"
            + "|fully|largely|partly|less|more|minimal|little)\\s+)?(?:AI\\s+)?(?:exposure|exposed|automatable"
            + "|automation\\s+(?:score|risk|rating|potential)|score|rating|band)\\b"
            + "|scores?\\s+(?:highly|high|low|lowly)\\s+(?:for|on)\\s+(?:automation|exposure|AI)\\b)");
    private static final Pattern CLAUSE_END = Pattern.compile("[,;:]|\\s(?:which|while|whereas|but)\\s");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");
    private static final java.util.Set<String> GENERIC_WORDS = java.util.Set.of("and", "with", "for", "the", "from",
            "into", "their", "your", "other", "about", "ensure", "ensuring", "provide", "providing", "manage",
            "managing", "work", "working", "team", "teams", "daily", "using", "make", "making", "support");

    /**
     * Internal assessment vocabulary that must not reach a visitor: the dimension labels, raw dimension ids and
     * direction or effect codes. Natural words such as trust, context, judgement or physical presence are fine; only
     * the labels are caught, and "original judgement" only when used as a high/low dimension label.
     */
    private static final List<Pattern> INTERNAL_JARGON = List.of(
            Pattern.compile("(?i)\\b(?:human\\s+)?trust\\s+dependency\\b"),
            Pattern.compile("(?i)\\b(?:task\\s+)?repeatability\\b"),
            Pattern.compile("(?i)\\b(?:digital\\s+)?executability\\b"),
            Pattern.compile("(?i)\\bphysical[-\\s]world\\s+dependency\\b"),
            Pattern.compile("(?i)\\bcontextual\\s+complexity\\b"),
            Pattern.compile("(?i)\\b(?:high|low|higher|lower)\\s+original\\s+judge?ments?\\b"
                    + "|\\boriginal\\s+judge?ment\\s+(?:dimension|score|factor|rating|level)\\b"),
            Pattern.compile("(?i)\\baccountability\\s+responsibility\\b"),
            Pattern.compile("(?i)\\binformation\\s+sufficiency\\b"),
            Pattern.compile("(?i)\\b(?:information_sufficient|task_repeatability|digital_executability|human_trust_dependency"
                    + "|physical_world_dependency|contextual_complexity|original_judgment|accountability_responsibility"
                    + "|ai_complementarity)\\b"),
            Pattern.compile("\\b(?:HIGH|LOW)_[A-Z]+(?:_[A-Z]+)*\\b|\\b(?:INCREASES|REDUCES)_AI_EXPOSURE\\b"));

    /** Advice to set aside, loosen or work around a rule, procedure, check or obligation. */
    private static final Pattern DEVIATION = Pattern.compile("(?i)\\b(?:ignor\\w*|bypass\\w*|skip\\w*|loosen\\w*"
            + "|relax\\w*|deviat\\w*|depart\\w*\\s+from|work(?:ing)?\\s+around|circumvent\\w*|overrid\\w*"
            + "|disregard\\w*|shortcut\\w*|cut(?:ting)?\\s+corners\\s+(?:on|with)|personali[sz]\\w*|abandon\\w*"
            + "|drop(?:ping)?|replac\\w*|bend(?:ing)?|waiv\\w*|set(?:ting)?\\s+aside)\\b");
    private static final Pattern REGULATED = Pattern.compile("(?i)\\b(?:procedures?|protocols?|checks?|checklists?"
            + "|polic(?:y|ies)|guidelines?|regulations?|regulatory\\s+requirements?|legal\\s+(?:obligations?"
            + "|requirements?)|professional\\s+standards?|standards\\s+of\\s+practice|security\\s+controls?|controls?"
            + "|safeguards?|confidentiality|privacy|data\\s+protection|consent|compliance"
            + "|(?:established|clinical|safety|standard|required|mandatory)\\s+routines?)\\b");
    /** Established routines or procedures together with personalising, varying or loosening them, anywhere in a sentence. */
    private static final Pattern ESTABLISHED_PROCEDURE = Pattern.compile("(?i)\\b(?:established|clinical|safety"
            + "|standard|required|mandatory|set)\\s+(?:routines?|procedures?|protocols?|processes?)\\b");
    private static final Pattern LOOSENING = Pattern.compile("(?i)\\b(?:personali[sz]\\w*|less\\s+repetitive|var(?:y|ied)"
            + "|deviat\\w*|depart\\w*|loosen\\w*|relax\\w*|bend\\w*)\\b");
    private static final Pattern SAFETY_NEGATION = Pattern.compile("(?i)\\b(?:never|not|no|without|avoid\\w*"
            + "|rather\\s+than|instead\\s+of)\\b|n't\\b");

    private static final Pattern ENUM_BAND_WORD = Pattern.compile("\\b(?:LOW|MODERATE|HIGH)\\b");
    private static final Pattern AMERICAN_SPELLING = Pattern.compile("(?i)\\b(?:judgments?|prioritiz\\w*"
            + "|personaliz\\w*|organiz\\w*|emphasiz\\w*|analyz\\w*|recogniz\\w*|behaviors?|optimiz\\w*|specializ\\w*"
            + "|utiliz\\w*|minimiz\\w*|maximiz\\w*)\\b");

    /**
     * Controlled vocabulary for each AI Exposure dimension, used to see which of Java's factors a sentence talks
     * about. It identifies factors in prose; it never decides or weighs one.
     */
    private static final Map<String, Pattern> DIMENSION_LANGUAGE = dimensionLanguage();

    /**
     * A claim about a factor's effect on AI Exposure: an effect verb whose object, within a few words of the same
     * clause, is AI Exposure ("raises your AI Exposure", "reduces exposure"), or a direct "higher/lower AI Exposure"
     * phrase. A word describing the level of a factor itself, such as "lower dependency on human trust" or
     * "increased use of digital systems", is not a claim about AI Exposure.
     */
    private static final Pattern RAISES_EXPOSURE = Pattern.compile("(?i)\\b(?:(?:increas(?:es|e|ing)|rais(?:es|e|ing)"
            + "|adds?\\s+to|adding\\s+to|push(?:es|ing)?\\s+up|drives?\\s+up|driving\\s+up|heighten(?:s|ing)?"
            + "|boost(?:s|ing)?)\\s+(?:\\w+\\s+){0,3}?(?:AI\\s+)?exposure|(?:more|higher|greater|increased)\\s+"
            + "(?:AI\\s+)?exposure)\\b");
    private static final Pattern LOWERS_EXPOSURE = Pattern.compile("(?i)\\b(?:(?:reduc(?:es|e|ing)|lower(?:s|ing)?"
            + "|decreas(?:es|e|ing)|offset(?:s|ting)?|dampen(?:s|ing)?|limit(?:s|ing)?)\\s+(?:\\w+\\s+){0,3}?(?:AI\\s+)?"
            + "exposure|(?:less|lower|reduced|decreased)\\s+(?:AI\\s+)?exposure)\\b");
    private static final Pattern NEGATION = Pattern.compile("(?i)\\b(?:not|never|no)\\b|n't\\b");

    /**
     * Words that say which state of a factor a phrase describes. "Little physical presence" is the low state of
     * physical-world dependency; "strong reliance on trust" the high state of human-trust dependency. An unqualified
     * mention ("physical presence", "human trust") describes the characteristic being present: the high state.
     */
    private static final java.util.Set<String> LOW_LEVEL_WORDS = java.util.Set.of("little", "less", "lower", "low",
            "limited", "minimal", "few", "fewer", "reduced", "rarely", "seldom", "cannot", "unable", "without",
            "hardly", "modest", "slight", "lesser");
    private static final java.util.Set<String> HIGH_LEVEL_WORDS = java.util.Set.of("much", "most", "more", "high",
            "higher", "strong", "strongly", "substantial", "substantially", "heavily", "heavy", "significant",
            "significantly", "considerable", "important", "importance", "central", "essential", "great", "greater",
            "extensive", "extensively", "largely", "mainly", "mostly", "deeply", "deep", "core", "crucial", "critical",
            "skilled", "increased", "considerably");
    /** Terms that themselves name a factor's low state, such as predictable work for contextual complexity. */
    private static final Map<String, Pattern> INHERENTLY_LOW = Map.of(
            "contextual_complexity", Pattern.compile("(?i)predictab\\w*|straightforward|simple|clear"),
            "task_repeatability", Pattern.compile("(?i)varied|variab\\w*|non-routine"));
    /** Phrase boundaries within a sentence; a qualifier only describes a factor inside the same phrase. */
    private static final Pattern PHRASE_BREAK = Pattern.compile(
            "(?i)[,;:]|\\b(?:while|whereas|but|which|although|though|because)\\b");

    /**
     * When Java selected no material factor on either side, the AI Exposure explanation must say so in substance:
     * no individual factor stood out, or the result reflects a combination of smaller characteristics.
     */
    private static final Pattern NO_MATERIAL_FACTOR_ACKNOWLEDGED = Pattern.compile("(?i)\\b(?:no|none\\s+of\\s+the)\\s+"
            + "(?:(?:single|individual|one|particular|specific|stand-?out|material|strong|dominant|clear)\\s+)*"
            + "(?:factors?|characteristics?|features?|aspects?|reasons?|drivers?)\\b"
            + "|\\b(?:combined|cumulative|overall|joint)\\s+effect\\s+of"
            + "|\\bcombination\\s+of\\s+(?:several|many|various|different|smaller|a\\s+number\\s+of)"
            + "|\\bseveral\\s+(?:smaller|modest|minor)\\b|\\bbalance\\s+of\\s+(?:several|many|different|various)\\b");
    private static final Pattern PHRASE_NEGATION = Pattern.compile("(?i)\\b(?:not|never|no|none|nothing)\\b|n't\\b");
    /**
     * Effect language checked against an empty side: the shared effect patterns plus the phrasings "AI Exposure
     * higher/up" and "protection from AI Exposure", which the direction rule does not need.
     */
    private static final List<Pattern> EMPTY_SIDE_RAISES = List.of(RAISES_EXPOSURE,
            Pattern.compile("(?i)\\b(?:AI\\s+)?exposure\\s+(?:higher|up)\\b"));
    private static final List<Pattern> EMPTY_SIDE_LOWERS = List.of(LOWERS_EXPOSURE,
            Pattern.compile("(?i)\\bprotect(?:s|ion|ing|ive)?\\s+(?:\\w+\\s+){0,3}?(?:from|against)\\s+(?:AI\\s+)?exposure\\b"
                    + "|\\b(?:AI\\s+)?exposure\\s+(?:lower|down)\\b"));
    /** "The main reason", "the key driver", "driven mainly by": attributing the result to one leading cause. */
    private static final Pattern MAIN_REASON_CLAIM = Pattern.compile("(?i)\\b(?:main|primary|principal|chief|dominant"
            + "|biggest|largest|leading|key|most\\s+important)\\s+(?:reasons?|drivers?|factors?|causes?|contributors?"
            + "|explanations?)\\b|\\bdriven\\s+(?:mainly|mostly|largely|primarily|chiefly)\\s+by\\b"
            + "|\\b(?:mainly|mostly|largely|primarily|chiefly)\\s+(?:because\\s+of|due\\s+to)\\b");

    private final AssessmentTrace trace;

    public ProfessionalNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    /** The trace records each rule's outcome; it never changes a verdict. */
    public ProfessionalNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    /** Text rules only, for a narrative with no assessment context to ground it against. */
    public ProfessionalNarrative validate(ProfessionalNarrative narrative) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.PROFESSIONAL, TEXT_RULES);
        validateText(narrative, checks, Set.of());
        checks.accepted();
        return narrative;
    }

    /** Every rule, including that the AI Exposure explanation follows the factors Java selected. */
    public ProfessionalNarrative validate(ProfessionalNarrative narrative, ProfessionalNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.PROFESSIONAL, CONTEXT_RULES);
        validateText(narrative, checks, ownWords(context));
        validateGrounding(narrative, context, checks);
        checks.accepted();
        return narrative;
    }

    private void validateText(ProfessionalNarrative narrative, NarrativeRuleChecks checks, Set<String> ownWords) {
        checks.check("NARRATIVE_PRESENT", narrative == null, () -> null, "generated narrative must not be null");
        checks.check("WELL_FORMED_STRUCTURED_OUTPUT", false, () -> null,
                "enforced when the provider response was mapped onto the narrative record");
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.exposureExplanation(), narrative.humanAdvantageExplanation(),
                narrative.complementarityExplanation(), narrative.adaptabilityExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        checks.check(NUMERIC, text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(NUMERIC_CLAIM, text),
                "narrative must not repeat numeric scores or percentage claims");
        claim(checks, FUTURE, text, FUTURE_CLAIMS,
                "narrative must describe the work as assessed today, not forecast its future");
        claim(checks, CAPABILITY, text, CAPABILITY_CLAIMS,
                "narrative must not make categorical claims about what AI can or cannot do");
        claim(checks, TASK_AUTOMATION, text, TASK_AUTOMATION_CLAIMS,
                "narrative must not claim that tasks or the work can be automated or performed by AI; the assessment "
                        + "is role-level and describes scope for assistance");
        claim(checks, JOB_LOSS, text, JOB_LOSS_CLAIMS,
                "narrative must not turn AI Exposure into job loss, replacement or job security");
        claim(checks, ADAPTABILITY, List.of(narrative.adaptabilityExplanation()), PERSONAL_ADAPTABILITY_CLAIMS,
                "Role Adaptability must describe the structure of the role, not the person");
        Optional<String> jargon = NarrativeClaimMatcher.firstUndisclaimed(text, INTERNAL_JARGON);
        checks.check(JARGON, jargon.isPresent(), () -> jargon.orElse(null),
                "narrative must describe factors in plain language, not internal assessment labels");
        Optional<String> unsafe = unsafeProcedureAdvice(text);
        checks.check(UNSAFE_PROCEDURE, unsafe.isPresent(), () -> unsafe.orElse(null),
                "narrative must never advise setting aside, loosening or working around procedures, safety checks, "
                        + "policies or legal and professional obligations");
        checks.check(BAND_WORDS, text.stream().anyMatch(value -> ENUM_BAND_WORD.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(ENUM_BAND_WORD, text),
                "narrative must use the natural band words low, moderate and high, not capitals");
        // A spelling the person used in their own submission may be quoted back; only the model's own is checked.
        Optional<String> american = text.stream()
                .flatMap(value -> AMERICAN_SPELLING.matcher(value).results().map(match -> match.group()))
                .filter(word -> !ownWords.contains(word.toLowerCase(Locale.ROOT)))
                .findFirst();
        checks.check(SPELLING, american.isPresent(), () -> american.orElse(null),
                "narrative must use British English spelling");
    }

    private void validateGrounding(ProfessionalNarrative narrative, ProfessionalNarrativeContext context,
            NarrativeRuleChecks checks) {
        Map<String, DecisionFactorEffect> supplied = new LinkedHashMap<>();
        context.exposureDrivers().forEach(factor -> supplied.put(factor.dimensionId(), factor.effect()));
        context.protectiveFactors().forEach(factor -> supplied.put(factor.dimensionId(), factor.effect()));
        String explanation = narrative.exposureExplanation();

        boolean grounded = supplied.isEmpty() || supplied.keySet().stream().anyMatch(id -> mentions(explanation, id));
        checks.check(GROUNDED, !grounded, () -> explanation,
                "the AI Exposure explanation must explain at least one of the factors Java selected");

        List<String> dominant = DominantExposureFactor.of(context);
        boolean leads = dominant.isEmpty() || dominant.stream().anyMatch(id -> mentions(explanation, id));
        checks.check(DOMINANT, !leads, () -> explanation,
                "the AI Exposure explanation must include the dominant factor Java selected for this result");

        Optional<String> contradiction = directionContradiction(explanation, supplied);
        checks.check(DIRECTIONS, contradiction.isPresent(), () -> contradiction.orElse(null),
                "the AI Exposure explanation must not reverse the direction of a factor Java selected");

        List<ProfessionalNarrativeContext.FactorContext> selected = Stream.concat(context.exposureDrivers().stream(),
                context.protectiveFactors().stream()).toList();
        Optional<String> wrongLevel = levelContradiction(explanation, selected);
        checks.check(LEVELS, wrongLevel.isPresent(), () -> wrongLevel.orElse(null),
                "the AI Exposure explanation must describe the same level of a factor that Java selected, for example "
                        + "little physical presence rather than physical presence");

        Optional<Pattern> occupationWide = occupationWideClaim(context.profession());
        List<String> text = new ArrayList<>(List.of(narrative.headline(), narrative.summary(),
                narrative.whatThisMeans(), narrative.exposureExplanation(), narrative.humanAdvantageExplanation(),
                narrative.complementarityExplanation(), narrative.adaptabilityExplanation()));
        text.addAll(narrative.practicalActions());
        Optional<String> broad = occupationWide.flatMap(pattern -> NarrativeClaimMatcher.firstUndisclaimed(text,
                List.of(pattern)));
        checks.check(OCCUPATION, broad.isPresent(), () -> broad.orElse(null),
                "narrative must explain the work the person described, not make claims about the whole occupation");

        List<String> everything = new ArrayList<>(text);
        everything.add(narrative.cautionOrLimitations());
        Optional<String> scored = responsibilityScored(everything, context.responsibilities(), context.profession());
        checks.check(RESPONSIBILITY_SCORING, scored.isPresent(), () -> scored.orElse(null),
                "narrative must not present a submitted responsibility as separately scored; the assessment is role-level");

        // Only for a side on which Java selected nothing material; a populated side is left to the rules above.
        Optional<String> unsupported = attributionToEmptySide(explanation, text,
                context.exposureDrivers().isEmpty(), context.protectiveFactors().isEmpty());
        checks.check(NO_MATERIAL_FACTORS, unsupported.isPresent(), () -> unsupported.orElse(null),
                "the narrative must not credit a characteristic with moving AI Exposure in a direction for which no "
                        + "factor was material enough to highlight; when nothing is material on either side, the AI "
                        + "Exposure explanation must say that no individual factor stood out and must not name a main "
                        + "reason");
    }

    /**
     * Attribution to a side Java left empty. With no material raising factor, no sentence may credit a characteristic
     * with raising AI Exposure; with no material lowering factor, none may credit one with lowering it. With both
     * sides empty, the AI Exposure explanation must also name no main reason and must acknowledge that no individual
     * factor stood out. Negated statements ("no single factor raises AI Exposure") are allowed. Returns the offending
     * sentence, or the whole explanation when the acknowledgement is missing.
     */
    private static Optional<String> attributionToEmptySide(String explanation, List<String> text,
            boolean raisingEmpty, boolean loweringEmpty) {
        List<Pattern> unsupportedEffects = new ArrayList<>();
        if (raisingEmpty) {
            unsupportedEffects.addAll(EMPTY_SIDE_RAISES);
        }
        if (loweringEmpty) {
            unsupportedEffects.addAll(EMPTY_SIDE_LOWERS);
        }
        for (String value : text) {
            for (String sentence : NarrativeClaimMatcher.sentences(value)) {
                if (DIMENSION_LANGUAGE.keySet().stream().noneMatch(id -> mentions(sentence, id))) {
                    continue;
                }
                Optional<String> claim = undeniedMatch(sentence, unsupportedEffects);
                if (claim.isPresent()) {
                    return claim;
                }
            }
        }
        if (!raisingEmpty || !loweringEmpty) {
            return Optional.empty();
        }
        for (String sentence : NarrativeClaimMatcher.sentences(explanation)) {
            Optional<String> mainReason = undeniedMatch(sentence, List.of(MAIN_REASON_CLAIM));
            if (mainReason.isPresent()) {
                return mainReason;
            }
        }
        return NO_MATERIAL_FACTOR_ACKNOWLEDGED.matcher(explanation).find() ? Optional.empty() : Optional.of(explanation);
    }

    /** The sentence, when one of the patterns matches it without a negation earlier in the same phrase. */
    private static Optional<String> undeniedMatch(String sentence, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher match = pattern.matcher(sentence);
            while (match.find()) {
                if (!negatedInPhrase(sentence, match.start())) {
                    return Optional.of(sentence.strip());
                }
            }
        }
        return Optional.empty();
    }

    /** Whether a negation appears earlier in the same phrase as the match at {@code position}. */
    private static boolean negatedInPhrase(String sentence, int position) {
        String before = sentence.substring(0, position);
        Matcher breaks = PHRASE_BREAK.matcher(before);
        int phraseStart = 0;
        while (breaks.find()) {
            phraseStart = breaks.end();
        }
        return PHRASE_NEGATION.matcher(before.substring(phraseStart)).find();
    }

    /**
     * A sentence that clearly says one way (raises or lowers exposure) about a dimension Java selected the other
     * way. Sentences that say both, or negate the effect, are left alone rather than guessed at.
     */
    private static Optional<String> directionContradiction(String explanation,
            Map<String, DecisionFactorEffect> supplied) {
        for (String sentence : NarrativeClaimMatcher.sentences(explanation)) {
            boolean raises = RAISES_EXPOSURE.matcher(sentence).find();
            boolean lowers = LOWERS_EXPOSURE.matcher(sentence).find();
            if (raises == lowers || NEGATION.matcher(sentence).find()) {
                continue;
            }
            DecisionFactorEffect claimed = raises
                    ? DecisionFactorEffect.INCREASES_AI_EXPOSURE : DecisionFactorEffect.REDUCES_AI_EXPOSURE;
            for (var factor : supplied.entrySet()) {
                if (factor.getValue() != claimed && mentions(sentence, factor.getKey())) {
                    return Optional.of(sentence.strip());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A sentence that names one of the person's submitted responsibilities and attaches an assessed classification
     * to it, for example "Monitoring patients is highly exposed to AI". A responsibility mentioned as an example of
     * the role-level result, or followed by a new clause, is left alone.
     *
     * <p>A responsibility is recognised by what it acts on, not by its action verb: "backend services" in "Design and
     * develop backend services", not "develop". Words of the person's own job title are never evidence, so "your role
     * as a Java Software Developer has moderate AI Exposure" is a role-level statement even when a responsibility
     * shares a stem with the title.</p>
     */
    private static Optional<String> responsibilityScored(List<String> texts, List<String> responsibilities,
            String profession) {
        java.util.Set<String> titleWords = new java.util.HashSet<>();
        WORD.matcher(profession).results().map(match -> match.group().toLowerCase(Locale.ROOT)).forEach(word -> {
            titleWords.add(word);
            titleWords.add(word + "s");
        });
        java.util.Set<String> stems = new java.util.HashSet<>();
        for (String responsibility : responsibilities) {
            responsibilitySubjects(responsibility).forEach(word -> stems.add(stem(word)));
        }
        if (stems.isEmpty()) {
            return Optional.empty();
        }
        for (String text : texts) {
            for (String sentence : NarrativeClaimMatcher.sentences(text)) {
                var words = WORD.matcher(sentence);
                while (words.find()) {
                    String word = words.group().toLowerCase(Locale.ROOT);
                    if (word.length() < 4 || titleWords.contains(word) || !stems.contains(stem(word))) {
                        continue;
                    }
                    String after = sentence.substring(words.end());
                    var clauseEnd = CLAUSE_END.matcher(after);
                    String clause = clauseEnd.find() ? after.substring(0, clauseEnd.start()) : after;
                    var classification = ASSESSED_CLASSIFICATION.matcher(clause);
                    if (classification.find() && !NarrativeClaimMatcher.disclaimed(
                            sentence.substring(0, words.end() + classification.end()))) {
                        return Optional.of(sentence.strip());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The words that identify what a responsibility is about: everything after its leading action verbs ("Design and
     * develop backend services" gives backend, services). A responsibility with nothing after its verb, such as a
     * noun phrase, keeps all its meaningful words.
     */
    private static List<String> responsibilitySubjects(String responsibility) {
        List<String> tokens = WORD.matcher(responsibility).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT)).toList();
        int index = tokens.isEmpty() ? 0 : 1;
        while (index + 1 < tokens.size() && (tokens.get(index).equals("and") || tokens.get(index).equals("or"))) {
            index += 2;
        }
        List<String> subjects = meaningful(tokens.subList(Math.min(index, tokens.size()), tokens.size()));
        return subjects.isEmpty() ? meaningful(tokens) : subjects;
    }

    private static List<String> meaningful(List<String> tokens) {
        return tokens.stream().filter(word -> word.length() >= 4 && !GENERIC_WORDS.contains(word)).toList();
    }

    /**
     * A sentence advising that a procedure, check, policy or obligation be set aside, loosened, personalised or
     * worked around. A deviation word directly followed by a regulated object counts, as does an established
     * routine or procedure mentioned together with personalising or loosening it. Negated advice ("never bypass…",
     * "without skipping…") is left alone, and keeping to procedures is never caught.
     */
    private static Optional<String> unsafeProcedureAdvice(List<String> texts) {
        for (String text : texts) {
            for (String sentence : NarrativeClaimMatcher.sentences(text)) {
                var deviation = DEVIATION.matcher(sentence);
                while (deviation.find()) {
                    String after = sentence.substring(deviation.end());
                    var clauseEnd = CLAUSE_END.matcher(after);
                    String clause = clauseEnd.find() ? after.substring(0, clauseEnd.start()) : after;
                    String nearby = String.join(" ", java.util.Arrays.stream(clause.trim().split("\\s+")).limit(6).toList());
                    if (REGULATED.matcher(nearby).find() && !negatedBefore(sentence, deviation.start())) {
                        return Optional.of(sentence.strip());
                    }
                }
                var established = ESTABLISHED_PROCEDURE.matcher(sentence);
                var loosening = LOOSENING.matcher(sentence);
                if (established.find() && loosening.find() && !negatedBefore(sentence, loosening.start())) {
                    return Optional.of(sentence.strip());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean negatedBefore(String sentence, int position) {
        String before = sentence.substring(0, position);
        var breaks = Pattern.compile("[,;:]|\\bbut\\b").matcher(before);
        int clauseStart = 0;
        while (breaks.find()) {
            clauseStart = breaks.end();
        }
        return SAFETY_NEGATION.matcher(before.substring(clauseStart)).find();
    }

    /** A crude shared stem, so "monitoring", "monitor" and "monitored" all match. */
    private static String stem(String word) {
        return word.length() <= 5 ? word : word.substring(0, 5);
    }

    /**
     * A sentence that claims an effect on AI Exposure while describing a selected factor at the opposite level from
     * the one Java selected, for example "physical presence raises AI Exposure" when Java selected little
     * physical-world presence. Java's level is read from the factor's own direction (HIGH_ or LOW_), which the
     * decision policy produced, so no knowledge of which dimensions are inverse is duplicated here. Sentences
     * without an effect claim, or that negate or mix effects, are not judged.
     */
    private static Optional<String> levelContradiction(String explanation,
            List<ProfessionalNarrativeContext.FactorContext> selected) {
        for (String sentence : NarrativeClaimMatcher.sentences(explanation)) {
            boolean raises = RAISES_EXPOSURE.matcher(sentence).find();
            boolean lowers = LOWERS_EXPOSURE.matcher(sentence).find();
            if (raises == lowers || NEGATION.matcher(sentence).find()) {
                continue;
            }
            // The effect phrase itself ("higher AI Exposure") says nothing about a factor's level.
            String withoutEffect = LOWERS_EXPOSURE.matcher(RAISES_EXPOSURE.matcher(sentence).replaceAll(" "))
                    .replaceAll(" ");
            String[] phrases = PHRASE_BREAK.split(withoutEffect);
            for (var factor : selected) {
                Pattern language = DIMENSION_LANGUAGE.get(factor.dimensionId());
                if (language == null) {
                    continue;
                }
                boolean selectedHigh = factor.direction().name().startsWith("HIGH_");
                boolean mentioned = false;
                boolean sameLevel = false;
                for (String phrase : phrases) {
                    var mention = language.matcher(phrase);
                    while (mention.find()) {
                        mentioned = true;
                        sameLevel |= describesHigh(phrase, mention.start(), mention.end(), mention.group(),
                                factor.dimensionId()) == selectedHigh;
                    }
                }
                if (mentioned && !sameLevel) {
                    return Optional.of(sentence.strip());
                }
            }
        }
        return Optional.empty();
    }

    /** Whether a mention describes the high state: the nearest level word in its phrase decides, else it is present. */
    private static boolean describesHigh(String phrase, int start, int end, String word, String dimensionId) {
        Pattern low = INHERENTLY_LOW.get(dimensionId);
        if (low != null && low.matcher(word).matches()) {
            return false;
        }
        String[] before = phrase.substring(0, start).toLowerCase(Locale.ROOT).split("[^a-z']+");
        for (int index = before.length - 1; index >= 0; index--) {
            String token = before[index].replace("can't", "cannot");
            if (LOW_LEVEL_WORDS.contains(token)) {
                return false;
            }
            if (HIGH_LEVEL_WORDS.contains(token)) {
                return true;
            }
        }
        String[] after = phrase.substring(end).toLowerCase(Locale.ROOT).split("[^a-z']+");
        for (int index = 0, seen = 0; index < after.length && seen < 4; index++) {
            if (after[index].isEmpty()) {
                continue;
            }
            seen++;
            if (LOW_LEVEL_WORDS.contains(after[index])) {
                return false;
            }
            if (HIGH_LEVEL_WORDS.contains(after[index])) {
                return true;
            }
        }
        return true;
    }

    private static boolean mentions(String text, String dimensionId) {
        Pattern language = DIMENSION_LANGUAGE.get(dimensionId);
        return language != null && language.matcher(text).find();
    }

    /** "Registered nurses are…", "engineers will…": a claim about everyone with the job title. */
    private static Optional<Pattern> occupationWideClaim(String profession) {
        String[] words = profession.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s-]", " ").trim().split("\\s+");
        String last = words[words.length - 1];
        if (last.length() < 3) {
            return Optional.empty();
        }
        String plural = Pattern.quote(last.endsWith("s") ? last : last + "s");
        return Optional.of(Pattern.compile("(?i)\\b(?:all\\s+|most\\s+|many\\s+)?" + plural
                + "\\s+(?:are|will|tend\\s+to|typically|generally|usually|often|must|always|cannot|can't)\\b"));
    }

    private static Set<String> ownWords(ProfessionalNarrativeContext context) {
        List<String> submitted = new ArrayList<>(List.of(context.profession(), context.workDescription()));
        submitted.addAll(context.responsibilities());
        submitted.addAll(context.skills());
        return submitted.stream()
                .flatMap(value -> Stream.of(value.toLowerCase(Locale.ROOT).split("[^a-z]+")))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void claim(NarrativeRuleChecks checks, String rule, List<String> text, List<Pattern> claims,
            String rejection) {
        Optional<String> sentence = NarrativeClaimMatcher.firstUndisclaimed(text, claims);
        checks.check(rule, sentence.isPresent(), () -> sentence.orElse(null), rejection);
    }

    private static Map<String, Pattern> dimensionLanguage() {
        Map<String, Pattern> language = new LinkedHashMap<>();
        language.put("task_repeatability", Pattern.compile("(?i)\\b(?:routines?|repetitive|repeat\\w*|standardi[sz]\\w*"
                + "|recurring|predictable\\s+(?:process|pattern|task)\\w*|set\\s+process\\w*|established\\s+process\\w*)\\b"));
        language.put("digital_executability", Pattern.compile("(?i)\\b(?:digital\\w*|computers?|software|online"
                + "|on-screen|screens?|electronic\\w*|data\\s+entry|virtual\\w*)\\b"));
        language.put("human_trust_dependency", Pattern.compile("(?i)\\b(?:trust\\w*|relationships?|rapport|empath\\w*"
                + "|interpersonal|persua\\w*|reassur\\w*|compassion\\w*|emotional\\w*|human\\s+(?:interaction|contact"
                + "|connection)|communicat\\w*)\\b"));
        language.put("physical_world_dependency", Pattern.compile("(?i)\\b(?:physical\\w*|hands-on|hands\\s+on"
                + "|in[-\\s]person|bedside|on[-\\s]site|manual\\w*|real[-\\s]world|presence|face[-\\s]to[-\\s]face)\\b"));
        language.put("contextual_complexity", Pattern.compile("(?i)\\b(?:context\\w*|complex\\w*|changing|unpredictab\\w*"
                + "|variab\\w*|varied|situations?|situational|circumstances?|nuanc\\w*|predictab\\w*)\\b"));
        language.put("original_judgment", Pattern.compile("(?i)\\b(?:judgements?|judgments?|original\\w*|creativ\\w*"
                + "|novel|synthes\\w*|new\\s+approach\\w*|established\\s+(?:approach|method)\\w*|discretion\\w*)\\b"));
        language.put("accountability_responsibility", Pattern.compile("(?i)\\b(?:accountab\\w*|responsib\\w*"
                + "|consequential|liabil\\w*|sign[-\\s]off)\\b"));
        return Map.copyOf(language);
    }

    private static List<Pattern> patterns(String... regexes) {
        return Stream.of(regexes).map(regex -> Pattern.compile("(?i)" + regex)).toList();
    }
}
