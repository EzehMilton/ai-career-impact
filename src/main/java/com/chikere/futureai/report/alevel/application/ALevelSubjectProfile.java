package com.chikere.futureai.report.alevel.application;

import java.util.List;
import java.util.Objects;

/**
 * The free {@code a-level-decision-v2} result as shown: deterministic copy over the stored decision, with no
 * overall score, no ranking of subjects and no AI-written text.
 *
 * @param caution the profile is READY_WITH_CAUTION: limited information or two or more lower-confidence measures
 */
public record ALevelSubjectProfile(
        String heading,
        boolean moreInformationRequired,
        boolean caution,
        String summary,
        Measure optionsKeptOpen,
        CapabilityProfile capabilityProfile,
        Measure learningAlongsideAi) {

    public static final String HEADING = "Your A-level subject profile";

    public ALevelSubjectProfile {
        Objects.requireNonNull(heading);
        Objects.requireNonNull(summary);
        if (!moreInformationRequired) {
            Objects.requireNonNull(optionsKeptOpen);
            Objects.requireNonNull(capabilityProfile);
            Objects.requireNonNull(learningAlongsideAi);
        }
    }

    /** Options Kept Open and Learning Alongside AI: the two single-score cards around the Capability Profile. */
    public List<Measure> singleMeasures() {
        return moreInformationRequired ? List.of() : List.of(optionsKeptOpen, learningAlongsideAi);
    }

    /**
     * One scored dimension.
     *
     * @param score the boundary-safe two-decimal score
     * @param confidenceNote empty when the measure needs no note; never a percentage
     */
    public record Measure(String id, String name, String score, String band, String bandLabel, String explanation,
            List<String> drivers, List<String> limits, String confidenceNote) {
        public Measure {
            drivers = List.copyOf(drivers);
            limits = List.copyOf(limits);
        }
    }

    /** The two capability sides, shown together and never added up or ranked. */
    public record CapabilityProfile(String note, Measure analyticalAndQuantitative,
            Measure communicationInterpretationAndContext) {

        /** Both sides, analytical first only by convention: the order is not a ranking. */
        public List<Measure> sides() {
            return List.of(analyticalAndQuantitative, communicationInterpretationAndContext);
        }
    }
}
