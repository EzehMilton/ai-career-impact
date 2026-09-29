package com.chikere.futureai.decision.professional;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ProfessionalDecisionResult(
        String policyVersion,
        double informationSufficiency,
        AssessmentDecision assessmentDecision,
        Map<ProfessionalMeasure, ProfessionalMeasureResult> measures,
        List<ProfessionalDecisionFactor> topExposureDrivers,
        List<ProfessionalDecisionFactor> topProtectiveFactors) {

    public ProfessionalDecisionResult {
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
        if (assessmentDecision.journey() != JourneyType.PROFESSIONAL) {
            throw new IllegalArgumentException("assessmentDecision must be for the PROFESSIONAL journey");
        }

        EnumMap<ProfessionalMeasure, ProfessionalMeasureResult> measureCopy =
                new EnumMap<>(ProfessionalMeasure.class);
        measureCopy.putAll(Objects.requireNonNull(measures, "measures must not be null"));
        measureCopy.forEach((measure, result) -> {
            Objects.requireNonNull(result, "measure result must not be null");
            if (measure != result.measure()) {
                throw new IllegalArgumentException("measure key must match its result");
            }
        });
        boolean informationRequired = assessmentDecision.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED;
        if (informationRequired && !measureCopy.isEmpty()) {
            throw new IllegalArgumentException("authoritative measures must be absent when more information is required");
        }
        if (!informationRequired && !measureCopy.keySet().equals(java.util.EnumSet.allOf(ProfessionalMeasure.class))) {
            throw new IllegalArgumentException("a completed professional decision must contain all four measures");
        }
        measures = Collections.unmodifiableMap(measureCopy);
        topExposureDrivers = List.copyOf(
                Objects.requireNonNull(topExposureDrivers, "topExposureDrivers must not be null"));
        topProtectiveFactors = List.copyOf(
                Objects.requireNonNull(topProtectiveFactors, "topProtectiveFactors must not be null"));
    }

    public Optional<ProfessionalMeasureResult> measure(ProfessionalMeasure measure) {
        return Optional.ofNullable(measures.get(Objects.requireNonNull(measure, "measure must not be null")));
    }
}
