package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileBand;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileFactor;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasureResult;

import java.util.List;
import java.util.Objects;

/** Plain-English v3 presentation of the unchanged {@code a-level-decision-v2} result. */
public final class ALevelSubjectProfileComposerV3 {

    static final String LOWER_CONFIDENCE_NOTE = "This part of your profile is less certain because some of the "
            + "judgements behind it were not clear-cut.";
    static final String CAPABILITY_NOTE = "These two areas show the different skills your subjects develop. Neither "
            + "is better. A lighter emphasis is part of the shape of your subject combination, not a weakness.";
    static final String MORE_INFORMATION = "We could not identify your subjects clearly enough to build a profile. "
            + "Please check the subject names and try again.";

    public ALevelSubjectProfile compose(ALevelProfileDecisionResult profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        AssessmentStatus status = profile.assessmentDecision().status();
        if (status == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return new ALevelSubjectProfile(ALevelSubjectProfile.HEADING, true, false, MORE_INFORMATION, null, null,
                    null);
        }
        var options = measure(profile, ALevelProfileMeasure.OPTIONS_KEPT_OPEN);
        var analytical = measure(profile, ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE);
        var communication = measure(profile, ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT);
        var learning = measure(profile, ALevelProfileMeasure.LEARNING_ALONGSIDE_AI);
        String summary = optionsSentence(options.band()) + " " + analyticalSentence(analytical.band()) + " "
                + communicationSentence(communication.band()) + " " + aiSentence(learning.band());
        return new ALevelSubjectProfile(ALevelSubjectProfile.HEADING, false,
                status == AssessmentStatus.READY_WITH_CAUTION, summary, options,
                new ALevelSubjectProfile.CapabilityProfile(CAPABILITY_NOTE, analytical, communication), learning);
    }

    public static String customerLabel(ALevelProfileMeasure measure) {
        return switch (Objects.requireNonNull(measure, "measure must not be null")) {
            case OPTIONS_KEPT_OPEN -> "Future study options";
            case ANALYTICAL_AND_QUANTITATIVE -> "Maths and problem-solving";
            case COMMUNICATION_INTERPRETATION_AND_CONTEXT -> "Writing, ideas and the wider world";
            case LEARNING_ALONGSIDE_AI -> "How AI can help you learn";
        };
    }

    public static String bandLabel(ALevelProfileMeasure measure, ALevelProfileBand band) {
        Objects.requireNonNull(band, "band must not be null");
        return switch (Objects.requireNonNull(measure, "measure must not be null")) {
            case OPTIONS_KEPT_OPEN -> switch (band) {
                case LOW -> "focused";
                case MODERATE -> "balanced";
                case HIGH -> "broad";
            };
            case ANALYTICAL_AND_QUANTITATIVE, COMMUNICATION_INTERPRETATION_AND_CONTEXT -> switch (band) {
                case LOW -> "lighter emphasis";
                case MODERATE -> "moderate emphasis";
                case HIGH -> "strong emphasis";
            };
            case LEARNING_ALONGSIDE_AI -> switch (band) {
                case LOW -> "low";
                case MODERATE -> "moderate";
                case HIGH -> "high";
            };
        };
    }

    static String explanation(ALevelProfileMeasure measure) {
        return switch (measure) {
            case OPTIONS_KEPT_OPEN -> "This shows how many different study directions your subject combination "
                    + "could support. It looks at how varied the subjects are and the different skills they develop. "
                    + "It does not check any university's entry requirements.";
            case ANALYTICAL_AND_QUANTITATIVE -> "This shows how much your subjects develop mathematical reasoning, "
                    + "problem-solving, analysis, statistics and structured thinking.";
            case COMMUNICATION_INTERPRETATION_AND_CONTEXT -> "This shows how much your subjects develop writing, "
                    + "argument and interpretation. It also looks at how they help you understand people, society "
                    + "and the wider context.";
            case LEARNING_ALONGSIDE_AI -> "This shows where AI tools could support explanation, practice, feedback, "
                    + "checking and revision. AI does not replace your own understanding, and it may not be allowed "
                    + "in assessed work.";
        };
    }

    private static String optionsSentence(String band) {
        return switch (ALevelProfileBand.valueOf(band)) {
            case LOW -> "Your subject combination has a clear academic focus and supports related future study "
                    + "directions.";
            case MODERATE -> "Your subjects support a balanced range of future study directions.";
            case HIGH -> "Your subjects keep a broad range of future study options open.";
        };
    }

    private static String analyticalSentence(String band) {
        return switch (ALevelProfileBand.valueOf(band)) {
            case LOW -> "They place a lighter emphasis on maths, analysis and problem-solving.";
            case MODERATE -> "They place a moderate emphasis on maths, analysis and problem-solving.";
            case HIGH -> "They place a strong emphasis on maths, analysis and problem-solving.";
        };
    }

    private static String communicationSentence(String band) {
        return switch (ALevelProfileBand.valueOf(band)) {
            case LOW -> "They place a lighter emphasis on writing, interpretation and understanding the wider world.";
            case MODERATE -> "They place a moderate emphasis on writing, interpretation and understanding the wider "
                    + "world.";
            case HIGH -> "They place a strong emphasis on writing, interpretation and understanding the wider world.";
        };
    }

    private static String aiSentence(String band) {
        return switch (ALevelProfileBand.valueOf(band)) {
            case LOW -> "There is limited scope to use AI to support your learning in these subjects.";
            case MODERATE -> "There is some scope to use AI to support your learning in these subjects.";
            case HIGH -> "There is plenty of scope to use AI to support your learning in these subjects.";
        };
    }

    private static ALevelSubjectProfile.Measure measure(ALevelProfileDecisionResult profile,
            ALevelProfileMeasure measure) {
        ALevelProfileMeasureResult result = profile.measure(measure).orElseThrow();
        return new ALevelSubjectProfile.Measure(measure.name(), customerLabel(measure),
                ALevelSubjectProfileComposer.score(result.indicator(), result.band()), result.band().name(),
                bandLabel(measure, result.band()), explanation(measure), phrases(result.drivers(), true),
                phrases(result.limitingFactors(), false), result.lowerConfidence() ? LOWER_CONFIDENCE_NOTE : "");
    }

    private static List<String> phrases(List<ALevelProfileFactor> factors, boolean driver) {
        return factors.stream().map(factor -> phrase(factor.dimensionId(), driver)).toList();
    }

    private static String phrase(String dimensionId, boolean driver) {
        return switch (dimensionId) {
            case ALevelDecisionPolicyV2.DISCIPLINE_FAMILY_COVERAGE -> driver
                    ? "Your subjects cover different areas of study"
                    : "Your subjects cover closely related areas of study";
            case ALevelDecisionPolicyV2.SUBJECT_SKILL_DIVERSITY -> driver
                    ? "Your subjects develop a varied mix of skills"
                    : "Your subjects develop similar kinds of skill";
            case ALevelDecisionPolicyV2.AI_LEARNING_SUPPORT -> driver
                    ? "AI could support several parts of learning these subjects"
                    : "Fewer parts of learning these subjects suit AI support";
            default -> {
                String capability = capability(dimensionId);
                yield driver ? capability : "Less emphasis on "
                        + Character.toLowerCase(capability.charAt(0)) + capability.substring(1);
            }
        };
    }

    private static String capability(String dimensionId) {
        return switch (dimensionId) {
            case ALevelDecisionPolicyV2.QUANTITATIVE_REASONING -> "Mathematical and statistical reasoning";
            case ALevelDecisionPolicyV2.ANALYTICAL_REASONING -> "Analysis and problem-solving";
            case ALevelDecisionPolicyV2.TECHNICAL_DIGITAL_FOUNDATION -> "Technical and digital work";
            case ALevelDecisionPolicyV2.WRITTEN_VERBAL_COMMUNICATION -> "Writing, explanation and argument";
            case ALevelDecisionPolicyV2.ORIGINAL_JUDGMENT_CREATIVITY -> "Interpretation, original thinking and design";
            case ALevelDecisionPolicyV2.CONTEXTUAL_SOCIAL_UNDERSTANDING ->
                    "Understanding people, society and historical context";
            default -> throw new IllegalArgumentException("no phrase for " + dimensionId);
        };
    }
}
