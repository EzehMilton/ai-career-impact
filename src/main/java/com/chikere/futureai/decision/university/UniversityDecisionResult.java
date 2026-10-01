package com.chikere.futureai.decision.university;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record UniversityDecisionResult(
        String policyVersion,
        double informationSufficiency,
        AssessmentDecision assessmentDecision,
        Map<UniversityMeasure, UniversityMeasureResult> measures,
        List<UniversityDecisionFactor> topStrengths,
        List<UniversityDecisionFactor> topDevelopmentOpportunities) {

    public UniversityDecisionResult {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        policyVersion = policyVersion.strip();
        if (!Double.isFinite(informationSufficiency)
                || informationSufficiency < 0.0
                || informationSufficiency > 1.0) {
            throw new IllegalArgumentException("informationSufficiency must be finite and between 0 and 1");
        }
        Objects.requireNonNull(assessmentDecision, "assessmentDecision must not be null");
        if (assessmentDecision.journey() != JourneyType.UNIVERSITY) {
            throw new IllegalArgumentException("assessmentDecision must be for the UNIVERSITY journey");
        }

        EnumMap<UniversityMeasure, UniversityMeasureResult> measureCopy = new EnumMap<>(UniversityMeasure.class);
        measureCopy.putAll(Objects.requireNonNull(measures, "measures must not be null"));
        measureCopy.forEach((measure, result) -> {
            Objects.requireNonNull(result, "measure result must not be null");
            if (measure != result.measure()) {
                throw new IllegalArgumentException("measure key must match its result");
            }
        });
        boolean informationRequired = assessmentDecision.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED;
        if (informationRequired && !measureCopy.isEmpty()) {
            throw new IllegalArgumentException(
                    "authoritative measures must be absent when more information is required");
        }
        if (!informationRequired && !measureCopy.keySet().equals(EnumSet.allOf(UniversityMeasure.class))) {
            throw new IllegalArgumentException("a completed university decision must contain all five measures");
        }
        measures = Collections.unmodifiableMap(measureCopy);
        topStrengths = List.copyOf(Objects.requireNonNull(topStrengths, "topStrengths must not be null"));
        topDevelopmentOpportunities = List.copyOf(Objects.requireNonNull(
                topDevelopmentOpportunities, "topDevelopmentOpportunities must not be null"));
    }

    public Optional<UniversityMeasureResult> measure(UniversityMeasure measure) {
        return Optional.ofNullable(measures.get(Objects.requireNonNull(measure, "measure must not be null")));
    }
}
