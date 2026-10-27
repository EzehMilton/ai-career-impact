package com.chikere.futureai.trace;

import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a narrative validator's rules in its own order and, while tracing, records each outcome.
 *
 * <p>The validator still decides: {@link #check} throws the same rejection at the same rule, as a
 * {@link NarrativeRuleViolation} (an {@link IllegalArgumentException}) that also names the rule. Tracing only adds the record of which rules passed,
 * which one rejected the narrative, the text that triggered it, and which rules were never reached.</p>
 */
public final class NarrativeRuleChecks {

    private final AssessmentTrace trace;
    private final JourneyType journey;
    private final List<String> rules;
    private final TraceStage stage;
    private final List<Map<String, Object>> results = new ArrayList<>();

    private NarrativeRuleChecks(AssessmentTrace trace, JourneyType journey, List<String> rules, TraceStage stage) {
        this.trace = trace;
        this.journey = journey;
        this.rules = List.copyOf(rules);
        this.stage = stage;
    }

    /** Starts one validation run over the named rules, in the order the validator applies them. */
    public static NarrativeRuleChecks start(AssessmentTrace trace, JourneyType journey, List<String> rules) {
        return start(trace, journey, rules, TraceStage.NARRATIVE_VALIDATION);
    }

    /** As {@link #start(AssessmentTrace, JourneyType, List)}, recording the outcome under another trace stage. */
    public static NarrativeRuleChecks start(AssessmentTrace trace, JourneyType journey, List<String> rules,
            TraceStage stage) {
        return new NarrativeRuleChecks(trace, journey, rules, stage);
    }

    /** Records one rule and throws the validator's rejection, naming the rule, if it was violated. */
    public void check(String rule, boolean violated, Supplier<String> trigger, String rejection) {
        String triggeringText = violated ? trigger.get() : null;
        if (trace.isEnabled()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rule", rule);
            result.put("passed", !violated);
            if (violated) {
                result.put("reason", rejection);
                result.put("triggeringText", triggeringText);
            }
            results.add(result);
        }
        if (violated) {
            emit(false, rule);
            throw new NarrativeRuleViolation(rule, rejection, triggeringText);
        }
    }

    /** Records that every rule passed and the narrative was accepted. */
    public void accepted() {
        emit(true, null);
    }

    /** The first match of {@code pattern} across {@code text}, for a rejection's triggering text. */
    public static String firstMatch(Pattern pattern, List<String> text) {
        for (String value : text) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    private void emit(boolean accepted, String rejectedBy) {
        trace.event(journey, stage, () -> {
            List<Map<String, Object>> all = new ArrayList<>(results);
            for (String rule : rules.subList(Math.min(results.size(), rules.size()), rules.size())) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("rule", rule);
                skipped.put("passed", null);
                skipped.put("reason", "NOT_EVALUATED: an earlier rule rejected the narrative");
                all.add(skipped);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outcome", accepted ? "ACCEPTED" : "REJECTED");
            payload.put("rejectedBy", rejectedBy);
            payload.put("rules", all);
            return payload;
        });
    }
}
