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
    static final String JOB_LOSS = "NO_JOB_LOSS_OR_SECURITY_INFERENCE";
    static final String ADAPTABILITY = "ROLE_ADAPTABILITY_IS_ROLE_LEVEL";
    static final String GROUNDED = "EXPOSURE_EXPLANATION_GROUNDED_IN_FACTORS";
    static final String DIRECTIONS = "EXPOSURE_FACTOR_DIRECTIONS_MATCH";
    static final String OCCUPATION = "NO_OCCUPATION_WIDE_CLAIMS";
    static final String BAND_WORDS = "NO_ENUM_STYLE_BAND_WORDS";
    static final String SPELLING = "BRITISH_ENGLISH_SPELLING";
    static final String RESPONSIBILITY_SCORING = "NO_RESPONSIBILITY_LEVEL_SCORING";

    private static final List<String> TEXT_RULES = List.of("NARRATIVE_PRESENT", "WELL_FORMED_STRUCTURED_OUTPUT",
            NUMERIC, FUTURE, CAPABILITY, JOB_LOSS, ADAPTABILITY, BAND_WORDS, SPELLING);
    private static final List<String> CONTEXT_RULES = Stream.concat(TEXT_RULES.stream(),
            Stream.of(GROUNDED, DIRECTIONS, OCCUPATION, RESPONSIBILITY_SCORING)).toList();

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

    private static final Pattern ENUM_BAND_WORD = Pattern.compile("\\b(?:LOW|MODERATE|HIGH)\\b");
    private static final Pattern AMERICAN_SPELLING = Pattern.compile("(?i)\\b(?:judgments?|prioritiz\\w*"
            + "|personaliz\\w*|organiz\\w*|emphasiz\\w*|analyz\\w*|recogniz\\w*|behaviors?|optimiz\\w*|specializ\\w*"
            + "|utiliz\\w*|minimiz\\w*|maximiz\\w*)\\b");

    /**
     * Controlled vocabulary for each AI Exposure dimension, used to see which of Java's factors a sentence talks
     * about. It identifies factors in prose; it never decides or weighs one.
     */
    private static final Map<String, Pattern> DIMENSION_LANGUAGE = dimensionLanguage();

    private static final Pattern RAISES_EXPOSURE = Pattern.compile("(?i)\\b(?:increas\\w*|rais\\w*|adds?\\s+to"
            + "|adding\\s+to|push\\w*\\s+up|drives?\\s+up|heighten\\w*|boost\\w*|(?:more|higher|greater)\\s+(?:AI\\s+)?"
            + "exposure)\\b");
    private static final Pattern LOWERS_EXPOSURE = Pattern.compile("(?i)\\b(?:reduc\\w*|lowers?|lowering|decreas\\w*"
            + "|offset\\w*|dampen\\w*|(?:less|lower|reduced)\\s+(?:AI\\s+)?exposure)\\b");
    private static final Pattern NEGATION = Pattern.compile("(?i)\\b(?:not|never|no)\\b|n't\\b");

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
        claim(checks, JOB_LOSS, text, JOB_LOSS_CLAIMS,
                "narrative must not turn AI Exposure into job loss, replacement or job security");
        claim(checks, ADAPTABILITY, List.of(narrative.adaptabilityExplanation()), PERSONAL_ADAPTABILITY_CLAIMS,
                "Role Adaptability must describe the structure of the role, not the person");
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

        Optional<String> contradiction = directionContradiction(explanation, supplied);
        checks.check(DIRECTIONS, contradiction.isPresent(), () -> contradiction.orElse(null),
                "the AI Exposure explanation must not reverse the direction of a factor Java selected");

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
        Optional<String> scored = responsibilityScored(everything, context.responsibilities());
        checks.check(RESPONSIBILITY_SCORING, scored.isPresent(), () -> scored.orElse(null),
                "narrative must not present a submitted responsibility as separately scored; the assessment is role-level");
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
     */
    private static Optional<String> responsibilityScored(List<String> texts, List<String> responsibilities) {
        java.util.Set<String> stems = new java.util.HashSet<>();
        for (String responsibility : responsibilities) {
            WORD.matcher(responsibility).results().map(match -> match.group().toLowerCase(Locale.ROOT))
                    .filter(word -> word.length() >= 4 && !GENERIC_WORDS.contains(word))
                    .forEach(word -> stems.add(stem(word)));
        }
        if (stems.isEmpty()) {
            return Optional.empty();
        }
        for (String text : texts) {
            for (String sentence : NarrativeClaimMatcher.sentences(text)) {
                var words = WORD.matcher(sentence);
                while (words.find()) {
                    String word = words.group().toLowerCase(Locale.ROOT);
                    if (word.length() < 4 || !stems.contains(stem(word))) {
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

    /** A crude shared stem, so "monitoring", "monitor" and "monitored" all match. */
    private static String stem(String word) {
        return word.length() <= 5 ? word : word.substring(0, 5);
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
