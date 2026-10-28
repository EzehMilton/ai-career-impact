package com.chikere.futureai.decision.alevel;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete {@code a-level-decision-v2} subject profile: every scored dimension, or none when more information is
 * required. There is no overall score.
 */
public record ALevelProfileDecisionResult(
        String policyVersion,
        double informationSufficiency,
        AssessmentDecision assessmentDecision,
        Map<ALevelProfileMeasure, ALevelProfileMeasureResult> measures) {

    public ALevelProfileDecisionResult {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (!Double.isFinite(informationSufficiency) || informationSufficiency < 0.0 || informationSufficiency > 1.0) {
            throw new IllegalArgumentException("informationSufficiency must be finite and between 0 and 1");
        }
        Objects.requireNonNull(assessmentDecision, "assessmentDecision must not be null");
        if (assessmentDecision.journey() != JourneyType.A_LEVEL) {
            throw new IllegalArgumentException("assessmentDecision must be for the A_LEVEL journey");
        }
        if (assessmentDecision.overallIndicator().isPresent()) {
            throw new IllegalArgumentException("a subject profile has no overall indicator");
        }
        EnumMap<ALevelProfileMeasure, ALevelProfileMeasureResult> copy = new EnumMap<>(ALevelProfileMeasure.class);
        copy.putAll(Objects.requireNonNull(measures, "measures must not be null"));
        copy.forEach((measure, result) -> {
            if (measure != Objects.requireNonNull(result, "measure result must not be null").measure()) {
                throw new IllegalArgumentException("measure key must match its result");
            }
        });
        boolean informationRequired = assessmentDecision.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED;
        if (informationRequired && !copy.isEmpty()) {
            throw new IllegalArgumentException("measures must be absent when more information is required");
        }
        if (!informationRequired && !copy.keySet().equals(EnumSet.allOf(ALevelProfileMeasure.class))) {
            throw new IllegalArgumentException("a completed subject profile must contain every scored dimension");
        }
        measures = Collections.unmodifiableMap(copy);
    }

    public Optional<ALevelProfileMeasureResult> measure(ALevelProfileMeasure measure) {
        return Optional.ofNullable(measures.get(Objects.requireNonNull(measure, "measure must not be null")));
    }
}
