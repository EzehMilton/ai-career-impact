package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A {@code university-decision-v2} course profile: three measures and no composite headline score. */
public record UniversityProfileDecisionResult(
        String policyVersion,
        double informationSufficiency,
        AssessmentDecision assessmentDecision,
        Map<UniversityProfileMeasure, UniversityProfileMeasureResult> measures) {

    public UniversityProfileDecisionResult {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (!Double.isFinite(informationSufficiency) || informationSufficiency < 0.0 || informationSufficiency > 1.0) {
            throw new IllegalArgumentException("informationSufficiency must be finite and between 0 and 1");
        }
        Objects.requireNonNull(assessmentDecision, "assessmentDecision must not be null");
        if (assessmentDecision.journey() != JourneyType.UNIVERSITY) {
            throw new IllegalArgumentException("assessmentDecision must be for the UNIVERSITY journey");
        }
        if (assessmentDecision.overallIndicator().isPresent()) {
            throw new IllegalArgumentException("a course profile has no overall indicator");
        }
        EnumMap<UniversityProfileMeasure, UniversityProfileMeasureResult> copy =
                new EnumMap<>(UniversityProfileMeasure.class);
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
        if (!informationRequired && !copy.keySet().equals(EnumSet.allOf(UniversityProfileMeasure.class))) {
            throw new IllegalArgumentException("a completed course profile must contain all three measures");
        }
        measures = Collections.unmodifiableMap(copy);
    }

    public Optional<UniversityProfileMeasureResult> measure(UniversityProfileMeasure measure) {
        return Optional.ofNullable(measures.get(Objects.requireNonNull(measure, "measure must not be null")));
    }
}
