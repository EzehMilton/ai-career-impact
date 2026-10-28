package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;
import com.chikere.futureai.trace.TraceStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guardrail for {@code university-paid-narrative-v1}. It rejects prose that is missing, that states a
 * number, or that contradicts or goes beyond the deterministic report: another band for a measure, a superseded or
 * overall score, an employment or salary prediction, a claim about what AI cannot do, permission to use AI in
 * assessment, or regulation the data does not support.
 *
 * <p>The checks are deliberately conservative pattern rules, not a language model: they catch the claims the
 * product must never make, and leave tone and quality to the prompt. Anything rejected is repaired once; if the
 * repair also fails, the report uses deterministic copy.</p>
 */
public final class UniversityPaidNarrativeValidator {

    public static final String PRESENT = "NARRATIVE_FIELDS_PRESENT";
    public static final String LENGTH = "FIELD_LENGTH";
    public static final String NUMERIC = "NO_NUMBERS_OR_SCORES";
    public static final String SALARY = "NO_SALARY_OR_PAY";
    public static final String EMPLOYMENT = "NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION";
    public static final String AI_CERTAINTY = "NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO";
    public static final String AI_PERMISSION = "NO_AI_PERMISSION_CLAIM";
    public static final String MEASURE_NAMES = "ONLY_THE_THREE_COURSE_PROFILE_MEASURES";
    public static final String BANDS = "BANDS_MATCH_THE_COURSE_PROFILE";
    public static final String OVERALL = "NO_OVERALL_SCORE_OR_RANKING";
    public static final String REGULATION = "NO_UNSUPPORTED_REGULATION_CLAIM";

    public static final List<String> RULES = List.of(PRESENT, LENGTH, NUMERIC, SALARY, EMPLOYMENT, AI_CERTAINTY,
            AI_PERMISSION, MEASURE_NAMES, BANDS, OVERALL, REGULATION);
    /** Every rule except a missing narrative: rejected prose gets its one repair. */
    public static final Set<String> REPAIRABLE = Set.copyOf(RULES.stream().filter(rule -> !rule.equals(PRESENT))
            .toList());

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern CURRENCY = Pattern.compile(
            "(?i)[£$€]|\\b(?:salary|salaries|pay\\s+(?:of|rise|level)|earn(?:s|ing)?\\b|income|wage)");
    private static final Pattern EMPLOYMENT_CLAIM = Pattern.compile("(?i)\\b(?:guarantee\\w*|will\\s+(?:get|find|secure"
            + "|land)\\s+(?:you\\s+)?(?:a\\s+)?(?:job|work|employment|role)|employab(?:le|ility)\\s+(?:is|will)"
            + "|job\\s+(?:security|prospects|loss|losses)|redundan\\w*|unemploy\\w*|obsolete"
            + "|(?:jobs?|roles?|professions?|occupations?|careers?)\\s+(?:will|may|could|are\\s+going\\s+to)"
            + "\\s+(?:disappear|vanish|be\\s+replaced|be\\s+automated)"
            + "|replace[sd]?\\s+(?:you|graduates|students|workers|people|professionals)"
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
    private static final Pattern SUPERSEDED_MEASURES = Pattern.compile("(?i)\\b(?:future\\s+readiness|career\\s+"
            + "adaptability|human\\s+differentiation|skill\\s+transferability|ai\\s+complementarity)\\b");
    private static final Pattern OVERALL_CLAIM = Pattern.compile("(?i)\\b(?:overall\\s+(?:score|rating|grade|result)"
            + "|strongest\\s+measure|weakest\\s+measure|ranks?\\s+(?:among|above|below)|ranked\\b|league\\s+table"
            + "|out\\s+of\\s+ten)");
    private static final Pattern REGULATION_CLAIM = Pattern.compile("(?i)\\bregulat(?:ed|ion|ory)\\b");
    private static final String BAND_WORDS = "(low|lower|moderate|high|higher|strong|weak|limited)";

    private final AssessmentTrace trace;

    public UniversityPaidNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    public UniversityPaidNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityPaidNarrative validate(UniversityPaidNarrative narrative, UniversityPaidNarrativeContext context) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.UNIVERSITY, RULES,
                TraceStage.PAID_NARRATIVE_VALIDATION);
        checks.check(PRESENT, narrative == null || narrative.fields().stream().anyMatch(String::isBlank),
                () -> null, "every narrative field must be present");
        List<String> fields = narrative.fields();
        checks.check(LENGTH, fields.stream().anyMatch(text -> text.length() > UniversityPaidNarrative.MAXIMUM_FIELD_LENGTH),
                () -> fields.stream().filter(text -> text.length() > UniversityPaidNarrative.MAXIMUM_FIELD_LENGTH)
                        .findFirst().map(text -> text.substring(0, 120)).orElse(null),
                "each narrative field must stay short");
        // Titles are data the narrative may quote; numbers inside them ("Year 2 Project") are not claims.
        List<String> prose = fields.stream().map(text -> withoutTitles(text, context)).toList();
        checks.check(NUMERIC, prose.stream().anyMatch(text -> DIGIT.matcher(text).find()),
                () -> sentence(DIGIT, prose), "the narrative must not state numbers or scores");
        claim(checks, SALARY, CURRENCY, prose, "the narrative must not mention salary or pay");
        claim(checks, EMPLOYMENT, EMPLOYMENT_CLAIM, prose,
                "the narrative must not predict employment, job loss or replacement");
        claim(checks, AI_CERTAINTY, AI_CERTAINTY_CLAIM, prose,
                "the narrative must not claim what AI cannot do or that any work is safe from AI");
        claim(checks, AI_PERMISSION, AI_PERMISSION_CLAIM, prose,
                "the narrative must not suggest AI use is permitted in assessed work");
        claim(checks, MEASURE_NAMES, SUPERSEDED_MEASURES, prose,
                "the narrative must use only the three course-profile measures");
        String mismatch = bandMismatch(prose, context);
        checks.check(BANDS, mismatch != null, () -> mismatch, "every band mentioned must match the course profile");
        claim(checks, OVERALL, OVERALL_CLAIM, prose, "the narrative must not create an overall score or ranking");
        checks.check(REGULATION, !context.professionalStandardsRelevant()
                        && prose.stream().anyMatch(text -> REGULATION_CLAIM.matcher(text).find()),
                () -> sentence(REGULATION_CLAIM, prose),
                "the narrative must not call the course regulated unless professional standards are relevant");
        checks.accepted();
        return narrative;
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

    private static String withoutTitles(String text, UniversityPaidNarrativeContext context) {
        List<String> titles = new ArrayList<>();
        titles.add(context.courseTitle());
        context.modules().forEach(module -> titles.add(module.title()));
        String stripped = text;
        for (String title : titles) {
            if (!title.isBlank()) {
                stripped = stripped.replaceAll("(?i)" + Pattern.quote(title), "this module");
            }
        }
        return stripped;
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
