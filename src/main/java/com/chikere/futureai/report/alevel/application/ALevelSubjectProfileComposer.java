package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileBand;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileFactor;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasureResult;
import com.chikere.futureai.report.BoundarySafeScore;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Words a stored {@code a-level-decision-v2} subject profile, deterministically. Factor phrases are neutral and
 * factual: they never compare the student or the combination with anyone else, and a lighter capability side is
 * described as the shape of the combination, never as a weakness.
 */
public final class ALevelSubjectProfileComposer {

    static final String LOWER_CONFIDENCE_NOTE = "This part of your profile is less certain, because some of the "
            + "judgements behind it were less clear-cut.";
    static final String CAPABILITY_NOTE = "These two sides describe what your subjects emphasise. Different "
            + "combinations build different strengths: neither side is better, and a lighter emphasis is the shape of "
            + "your combination, not a weakness.";
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
        String summary = "Your subjects keep a " + lower(options.bandLabel()) + " range of options open. They place "
                + lower(analytical.bandLabel()) + " on analytical and quantitative work and "
                + lower(communication.bandLabel()) + " on communication, interpretation and context. AI tools may "
                + "offer a " + lower(learning.bandLabel()) + " level of support for learning in these subjects.";
        return new ALevelSubjectProfile(ALevelSubjectProfile.HEADING, false,
                status == AssessmentStatus.READY_WITH_CAUTION, summary, options,
                new ALevelSubjectProfile.CapabilityProfile(CAPABILITY_NOTE, analytical, communication), learning);
    }

    /** The band in the measure's own words. */
    public static String bandLabel(ALevelProfileMeasure measure, ALevelProfileBand band) {
        Objects.requireNonNull(band, "band must not be null");
        return switch (Objects.requireNonNull(measure, "measure must not be null")) {
            case OPTIONS_KEPT_OPEN -> switch (band) {
                case LOW -> "Focused";
                case MODERATE -> "Balanced";
                case HIGH -> "Broad";
            };
            case ANALYTICAL_AND_QUANTITATIVE, COMMUNICATION_INTERPRETATION_AND_CONTEXT -> switch (band) {
                case LOW -> "Lighter emphasis";
                case MODERATE -> "Moderate emphasis";
                case HIGH -> "Strong emphasis";
            };
            case LEARNING_ALONGSIDE_AI -> switch (band) {
                case LOW -> "Low";
                case MODERATE -> "Moderate";
                case HIGH -> "High";
            };
        };
    }

    /** The score as shown, never visually reaching the next band while the stored band is lower. */
    public static String score(double indicator, ALevelProfileBand band) {
        return BoundarySafeScore.format(indicator, switch (Objects.requireNonNull(band, "band must not be null")) {
            case LOW -> OptionalDouble.of(ALevelDecisionPolicyV2.MODERATE_FROM);
            case MODERATE -> OptionalDouble.of(ALevelDecisionPolicyV2.HIGH_FROM);
            case HIGH -> OptionalDouble.empty();
        });
    }

    static String explanation(ALevelProfileMeasure measure) {
        return switch (measure) {
            case OPTIONS_KEPT_OPEN -> "How broadly your subjects support different directions of further study: how "
                    + "many distinct academic families they cover, and how different the capabilities they build are. "
                    + "It is not a check of any university's entry requirements.";
            case ANALYTICAL_AND_QUANTITATIVE -> "How much mathematical, statistical, analytical and technical work "
                    + "your subjects build.";
            case COMMUNICATION_INTERPRETATION_AND_CONTEXT -> "How much writing, argument, interpretation and "
                    + "understanding of human and social context your subjects build.";
            case LEARNING_ALONGSIDE_AI -> "How much of the learning in your subjects AI tools can support, through "
                    + "explanation, practice, feedback and exploring sources. AI support does not replace developing "
                    + "your own understanding, and it does not mean AI is allowed in assessed work.";
        };
    }

    /** Neutral, factual phrases; never a comparison with other students or combinations. */
    static String phrase(String dimensionId, boolean driver) {
        return switch (dimensionId) {
            case ALevelDecisionPolicyV2.DISCIPLINE_FAMILY_COVERAGE -> driver
                    ? "Your subjects span distinct academic families"
                    : "Your subjects sit within closely related academic families";
            case ALevelDecisionPolicyV2.SUBJECT_SKILL_DIVERSITY -> driver
                    ? "Your subjects build different kinds of capability"
                    : "Your subjects build similar kinds of capability";
            case ALevelDecisionPolicyV2.AI_LEARNING_SUPPORT -> driver
                    ? "Much of the learning in these subjects lends itself to AI support"
                    : "Less of the learning in these subjects lends itself to AI support";
            default -> {
                String capability = capability(dimensionId);
                yield driver ? capability
                        : "Less emphasis on " + Character.toLowerCase(capability.charAt(0)) + capability.substring(1);
            }
        };
    }

    private static String capability(String dimensionId) {
        return switch (dimensionId) {
            case ALevelDecisionPolicyV2.QUANTITATIVE_REASONING -> "Mathematical and statistical reasoning";
            case ALevelDecisionPolicyV2.ANALYTICAL_REASONING -> "Structured analysis and problem solving";
            case ALevelDecisionPolicyV2.TECHNICAL_DIGITAL_FOUNDATION -> "Technical and computational work";
            case ALevelDecisionPolicyV2.WRITTEN_VERBAL_COMMUNICATION -> "Extended writing and argument";
            case ALevelDecisionPolicyV2.ORIGINAL_JUDGMENT_CREATIVITY -> "Interpretation, originality and design";
            case ALevelDecisionPolicyV2.CONTEXTUAL_SOCIAL_UNDERSTANDING ->
                    "Understanding human, social and historical context";
            default -> throw new IllegalArgumentException("no phrase for " + dimensionId);
        };
    }

    private static ALevelSubjectProfile.Measure measure(ALevelProfileDecisionResult profile,
            ALevelProfileMeasure measure) {
        ALevelProfileMeasureResult result = profile.measure(measure).orElseThrow();
        return new ALevelSubjectProfile.Measure(measure.name(), measure.displayName(),
                score(result.indicator(), result.band()), result.band().name(), bandLabel(measure, result.band()),
                explanation(measure), phrases(result.drivers(), true), phrases(result.limitingFactors(), false),
                result.lowerConfidence() ? LOWER_CONFIDENCE_NOTE : "");
    }

    private static List<String> phrases(List<ALevelProfileFactor> factors, boolean driver) {
        return factors.stream().map(factor -> phrase(factor.dimensionId(), driver)).toList();
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
