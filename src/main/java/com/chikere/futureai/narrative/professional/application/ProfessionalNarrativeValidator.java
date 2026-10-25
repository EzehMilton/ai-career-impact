package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Small deterministic guardrail applied after typed provider mapping. */
public final class ProfessionalNarrativeValidator {

    private static final List<String> RULES = List.of("NARRATIVE_PRESENT", "WELL_FORMED_STRUCTURED_OUTPUT",
            "NO_NUMERIC_SCORES_OR_PERCENTAGES", "NO_CATEGORICAL_AI_CAPABILITY_OR_JOB_LOSS_CLAIMS");

    // No trailing \\b: a percent sign is not a word character, so "92%" would otherwise slip through.
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|/\\s*10\\b|out\\s+of\\s+10\\b)");
    private static final Pattern CATEGORICAL_AI_CLAIM = Pattern.compile(
            "(?i)\\b(?:AI\\s+cannot\\s+(?:replicate|perform|do)\\b"
                    + "|AI\\s+will\\s+replace\\b"
                    + "|(?:this|the)\\s+(?:role|career|job)\\s+is\\s+safe\\s+from\\s+AI\\b"
                    + "|(?:this|the)\\s+(?:role|career|job)\\s+will\\s+disappear\\b)");

    private final AssessmentTrace trace;

    public ProfessionalNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    /** The trace records each rule's outcome; the rules and their order are unchanged. */
    public ProfessionalNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ProfessionalNarrative validate(ProfessionalNarrative narrative) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.PROFESSIONAL, RULES);
        checks.check("NARRATIVE_PRESENT", narrative == null, () -> null, "generated narrative must not be null");
        checks.check("WELL_FORMED_STRUCTURED_OUTPUT", false, () -> null,
                "enforced when the provider response was mapped onto the narrative record");
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.exposureExplanation(), narrative.humanAdvantageExplanation(),
                narrative.complementarityExplanation(), narrative.adaptabilityExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        checks.check("NO_NUMERIC_SCORES_OR_PERCENTAGES",
                text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(NUMERIC_CLAIM, text),
                "narrative must not repeat numeric scores or percentage claims");
        checks.check("NO_CATEGORICAL_AI_CAPABILITY_OR_JOB_LOSS_CLAIMS",
                text.stream().anyMatch(value -> CATEGORICAL_AI_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(CATEGORICAL_AI_CLAIM, text),
                "narrative must not make categorical AI capability or job-loss claims");
        checks.accepted();
        return narrative;
    }
}
