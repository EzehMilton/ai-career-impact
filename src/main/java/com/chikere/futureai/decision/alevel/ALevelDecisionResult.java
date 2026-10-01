package com.chikere.futureai.decision.alevel;

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

public record ALevelDecisionResult(
        String policyVersion,
        double informationSufficiency,
        AssessmentDecision assessmentDecision,
        Map<ALevelMeasure, ALevelMeasureResult> measures,
        List<ALevelDecisionFactor> topStrengths,
        List<ALevelDecisionFactor> topDevelopmentOpportunities) {

    public ALevelDecisionResult {
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
        if (assessmentDecision.journey() != JourneyType.A_LEVEL) {
            throw new IllegalArgumentException("assessmentDecision must be for the A_LEVEL journey");
        }

        EnumMap<ALevelMeasure, ALevelMeasureResult> measureCopy = new EnumMap<>(ALevelMeasure.class);
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
        if (!informationRequired && !measureCopy.keySet().equals(EnumSet.allOf(ALevelMeasure.class))) {
            throw new IllegalArgumentException("a completed A-level decision must contain all six measures");
        }
        measures = Collections.unmodifiableMap(measureCopy);
        topStrengths = List.copyOf(Objects.requireNonNull(topStrengths, "topStrengths must not be null"));
        topDevelopmentOpportunities = List.copyOf(Objects.requireNonNull(
                topDevelopmentOpportunities, "topDevelopmentOpportunities must not be null"));
    }

    public Optional<ALevelMeasureResult> measure(ALevelMeasure measure) {
        return Optional.ofNullable(measures.get(Objects.requireNonNull(measure, "measure must not be null")));
    }
}
