package com.chikere.futureai.trace;

/**
 * A narrative rejected by a named validation rule. Still an {@link IllegalArgumentException}, as validators have
 * always thrown, but it also says which rule failed and the text that triggered it, so a caller can ask for a
 * targeted correction.
 */
public final class NarrativeRuleViolation extends IllegalArgumentException {

    private final String rule;
    private final String triggeringText;

    public NarrativeRuleViolation(String rule, String reason, String triggeringText) {
        super(reason);
        this.rule = rule;
        this.triggeringText = triggeringText;
    }

    public String rule() {
        return rule;
    }

    public String reason() {
        return getMessage();
    }

    /** The offending sentence or value, or null where a rule has none (for example a missing narrative). */
    public String triggeringText() {
        return triggeringText;
    }
}
