package com.chikere.futureai.narrative.alevel.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;
import com.chikere.futureai.trace.TraceStage;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guardrail for {@code a-level-paid-narrative-v1}. It rejects prose that is missing, that states a
 * number, or that contradicts or goes beyond the deterministic report: an admission or eligibility promise, an
 * invented entry or grade requirement, an employment or salary claim, a claim about what AI cannot do, permission to
 * use AI in assessed work, an overall score or ranking, a band that differs from the stored one, or superseded v1
 * terminology.
 *
 * <p>The checks are deliberately conservative pattern rules, not a language model: they catch the claims the product
 * must never make and leave tone to the prompt. Anything rejected is repaired once; if the repair also fails, the
 * report uses deterministic copy.</p>
 */
public final class ALevelPaidNarrativeValidator {

    public static final String PRESENT = "NARRATIVE_FIELDS_PRESENT";
    public static final String LENGTH = "FIELD_LENGTH";
    public static final String NUMERIC = "NO_NUMBERS_SCORES_OR_PERCENTAGES";
    public static final String ADMISSION = "NO_ADMISSION_OR_ELIGIBILITY_CLAIM";
    public static final String REQUIREMENTS = "NO_INVENTED_ENTRY_OR_GRADE_REQUIREMENT";
    public static final String SALARY = "NO_SALARY_OR_PAY";
    public static final String EMPLOYMENT = "NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION";
    public static final String AI_CERTAINTY = "NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO";
    public static final String AI_PERMISSION = "NO_AI_PERMISSION_CLAIM";
    public static final String LEGACY_TERMS = "NO_SUPERSEDED_V1_TERMINOLOGY";
    public static final String BANDS = "BANDS_MATCH_THE_SUBJECT_PROFILE";
    public static final String RANKING = "NO_OVERALL_SCORE_OR_RANKING";

    public static final List<String> RULES = List.of(PRESENT, LENGTH, NUMERIC, ADMISSION, REQUIREMENTS, SALARY,
            EMPLOYMENT, AI_CERTAINTY, AI_PERMISSION, LEGACY_TERMS, BANDS, RANKING);
    /** Every rule except a missing narrative: rejected prose gets its one repair. */
    public static final Set<String> REPAIRABLE = Set.copyOf(RULES.stream().filter(rule -> !rule.equals(PRESENT))
            .toList());

    /** Phrases a narrative may use although they contain digits; they are names, not claims. */
    private static final Pattern PERMITTED_NUMBERED_PHRASES = Pattern.compile("(?i)\\byear\\s+1[23]\\b|\\bpost-18\\b"
            + "|\\ba[- ]levels?\\b");
    private static final Pattern DIGIT_OR_PERCENT = Pattern.compile("\\d|%|\\bper\\s?cent\\b");
    private static final Pattern ADMISSION_CLAIM = Pattern.compile("(?i)\\b(?:guarantee\\w*|you\\s+will\\s+(?:get\\s+"
            + "(?:in|into|a\\s+place|an\\s+offer)|be\\s+(?:accepted|admitted|offered|eligible))|(?:certain|sure)\\s+to\\s+"
            + "(?:get|be)\\s+(?:in|into|accepted|offered)|universities\\s+will\\s+(?:accept|want|offer)"
            + "|(?:accepted|admitted)\\s+(?:to|onto|into)\\s+(?:any|most|all|top)|(?:makes|make)\\s+you\\s+eligible"
            + "|eligible\\s+for\\s+(?:any|all|most|top)|admission\\s+(?:chances|likelihood|prospects)"
            + "|(?:strong|good|high)\\s+chance\\s+of\\s+(?:an?\\s+)?(?:offer|place|admission))");
    private static final Pattern REQUIREMENT_CLAIM = Pattern.compile("(?i)\\b(?:(?:requires?|required|need|needs)\\s+"
            + "(?:an?\\s+)?(?:a[- ]level\\s+)?(?:in\\s+)?(?:mathematics|maths|further\\s+maths|chemistry|physics|biology"
            + "|english|history|a\\s+science)|entry\\s+requirements?\\s+(?:are|is|include|includes)"
            + "|universit(?:y|ies)\\s+(?:require|expect|ask\\s+for)|grade\\s+requirements?\\s+(?:are|is|of)"
            + "|(?:a\\*|aaa|aab|abb|bbb)\\b)");
    private static final Pattern SALARY_CLAIM = Pattern.compile(
            "(?i)[£$€]|\\b(?:salary|salaries|pay\\s+(?:of|rise|level)|earn(?:s|ing)?\\b|income|wage)");
    private static final Pattern EMPLOYMENT_CLAIM = Pattern.compile("(?i)\\b(?:will\\s+(?:get|find|secure|land)\\s+"
            + "(?:you\\s+)?(?:a\\s+)?(?:job|work|employment|career)|employab(?:le|ility)|job\\s+(?:security|prospects"
            + "|loss|losses)|redundan\\w*|unemploy\\w*|obsolete|(?:jobs?|roles?|careers?)\\s+(?:will|may|could)\\s+"
            + "(?:disappear|vanish|be\\s+replaced|be\\s+automated)|replace[sd]?\\s+(?:you|students|workers|people)"
            + "|in\\s+demand\\s+by\\s+employers)");
    private static final Pattern AI_CERTAINTY_CLAIM = Pattern.compile("(?i)\\b(?:ai|machines?|computers?)\\s+"
            + "(?:cannot|can\\s*not|can't|will\\s+never|could\\s+never)\\b|\\bai[-\\s]?proof\\b|\\bfuture[-\\s]?proof\\b"
            + "|\\bsafe\\s+from\\s+(?:ai|automation)\\b|\\bautomation[-\\s]?proof\\b|\\bimmune\\s+to\\s+(?:ai|automation)\\b");
    private static final Pattern AI_PERMISSION_CLAIM = Pattern.compile("(?i)\\b(?:allowed|permitted|free|fine|"
            + "acceptable|ok(?:ay)?)\\s+to\\s+(?:use|submit)\\s+ai\\b|\\bai\\s+(?:use\\s+)?is\\s+(?:allowed|permitted|"
            + "acceptable)\\b|\\b(?:submit|hand\\s+in)\\s+(?:ai[-\\s]generated|ai[-\\s]written)\\b"
            + "|\\b(?:let|have)\\s+ai\\s+(?:write|complete|do)\\s+(?:your|the)\\s+(?:assessed|assessment|coursework"
            + "|essay|nea|homework|project)");
    private static final Pattern LEGACY_TERM = Pattern.compile("(?i)\\b(?:future\\s+optionality|ai[-\\s]era\\s+"
            + "adaptability|ai\\s+career\\s+impact|future\\s+readiness|degree\\s+pathway\\s+breadth|human\\s+and\\s+"
            + "creative\\s+strength)\\b");
    private static final Pattern RANKING_CLAIM = Pattern.compile("(?i)\\b(?:overall\\s+(?:score|rating|grade|result)"
            + "|(?:best|strongest|weakest|worst|top)\\s+(?:subject\\s+)?(?:combination|subjects|choice|measure|side)"
            + "|better\\s+than\\s+(?:other|most|many)|ranks?\\s+(?:among|above|below)|ranked\\b|league\\s+table"
            + "|out\\s+of\\s+ten)");
    /** The band words each measure may be described with, and the stored band each one means. */
    private static final Map<String, String> BAND_WORDS = Map.ofEntries(
            Map.entry("focused", "Focused"), Map.entry("balanced", "Balanced"), Map.entry("broad", "Broad"),
            Map.entry("lighter", "Lighter emphasis"), Map.entry("moderate", "Moderate"),
            Map.entry("strong", "Strong emphasis"), Map.entry("low", "Low"), Map.entry("high", "High"));

    private final AssessmentTrace trace;

    public ALevelPaidNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    public ALevelPaidNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ALevelPaidNarrative validate(ALevelPaidNarrative narrative, ALevelPaidNarrativeContext context) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.A_LEVEL, RULES,
                TraceStage.PAID_NARRATIVE_VALIDATION);
        checks.check(PRESENT, narrative == null || narrative.fields().stream().anyMatch(String::isBlank),
                () -> null, "every narrative field must be present");
        List<String> fields = narrative.fields();
        checks.check(LENGTH, fields.stream().anyMatch(text -> text.length() > ALevelPaidNarrative.MAXIMUM_FIELD_LENGTH),
                () -> fields.stream().filter(text -> text.length() > ALevelPaidNarrative.MAXIMUM_FIELD_LENGTH)
                        .findFirst().map(text -> text.substring(0, 120)).orElse(null),
                "each narrative field must stay short");
        // Subject and preference names are data the narrative may quote: digits inside them are not claims, so only
        // the number rule reads the text with them removed. Every claim rule reads the prose exactly as written,
        // because an invented requirement often names the student's own subjects.
        List<String> withoutNames = fields.stream().map(text -> withoutNames(text, context)).toList();
        List<String> prose = fields;
        claim(checks, NUMERIC, DIGIT_OR_PERCENT, withoutNames,
                "the narrative must not state numbers, scores or percentages");
        claim(checks, ADMISSION, ADMISSION_CLAIM, prose,
                "the narrative must not promise admission, an offer or course eligibility");
        claim(checks, REQUIREMENTS, REQUIREMENT_CLAIM, prose,
                "the narrative must not state entry, subject or grade requirements");
        claim(checks, SALARY, SALARY_CLAIM, prose, "the narrative must not mention salary or pay");
        claim(checks, EMPLOYMENT, EMPLOYMENT_CLAIM, prose,
                "the narrative must not predict employment, job loss or replacement");
        claim(checks, AI_CERTAINTY, AI_CERTAINTY_CLAIM, prose,
                "the narrative must not claim what AI cannot do or that anything is safe from AI");
        claim(checks, AI_PERMISSION, AI_PERMISSION_CLAIM, prose,
                "the narrative must not suggest AI use is permitted in assessed work");
        claim(checks, LEGACY_TERMS, LEGACY_TERM, prose, "the narrative must not use superseded v1 terminology");
        String mismatch = bandMismatch(prose, context);
        checks.check(BANDS, mismatch != null, () -> mismatch, "every band mentioned must match the subject profile");
        claim(checks, RANKING, RANKING_CLAIM, prose,
                "the narrative must not create an overall score or rank subjects or combinations");
        checks.accepted();
        return narrative;
    }

    private static void claim(NarrativeRuleChecks checks, String rule, Pattern pattern, List<String> prose,
            String rejection) {
        checks.check(rule, prose.stream().anyMatch(text -> pattern.matcher(text).find()),
                () -> sentence(pattern, prose), rejection);
    }

    /** A band word immediately before a measure name ("balanced Options Kept Open") must match its stored band. */
    private static String bandMismatch(List<String> prose, ALevelPaidNarrativeContext context) {
        for (var measure : context.measures()) {
            Pattern mention = Pattern.compile("(?i)\\b(focused|balanced|broad|lighter|moderate|strong|low|high)"
                    + "(?:\\s+emphasis\\s+on)?\\s+" + Pattern.quote(measure.name()));
            for (String text : prose) {
                Matcher matcher = mention.matcher(text);
                while (matcher.find()) {
                    String band = BAND_WORDS.get(matcher.group(1).toLowerCase(Locale.ROOT));
                    if (!measure.band().toLowerCase(Locale.ROOT).startsWith(band.toLowerCase(Locale.ROOT)
                            .split(" ")[0])) {
                        return matcher.group();
                    }
                }
            }
        }
        return null;
    }

    private static String withoutNames(String text, ALevelPaidNarrativeContext context) {
        String stripped = text;
        for (String name : context.quotableNames()) {
            if (!name.isBlank()) {
                stripped = stripped.replaceAll("(?i)" + Pattern.quote(name), "this subject");
            }
        }
        return PERMITTED_NUMBERED_PHRASES.matcher(stripped).replaceAll("this stage");
    }

    private static String sentence(Pattern pattern, List<String> prose) {
        for (String text : prose) {
            for (String sentence : text.split("(?<=[.!?])\\s+")) {
                if (pattern.matcher(sentence).find()) {
                    return sentence.strip();
                }
            }
        }
        return null;
    }
}
