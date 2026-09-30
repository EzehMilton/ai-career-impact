package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** The complete, deliberately limited input to narrative generation. */
public record ProfessionalNarrativeContext(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsExperience,
        AssessmentStatus assessmentStatus,
        AssessmentConfidence assessmentConfidence,
        List<MeasureContext> measures,
        List<FactorContext> exposureDrivers,
        List<FactorContext> protectiveFactors,
        AssessmentSource assessmentSource,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public ProfessionalNarrativeContext {
        profession = required(profession, "profession");
        workDescription = required(workDescription, "workDescription");
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities, "responsibilities must not be null"));
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
        Objects.requireNonNull(yearsExperience, "yearsExperience must not be null");
        Objects.requireNonNull(assessmentStatus, "assessmentStatus must not be null");
        Objects.requireNonNull(assessmentConfidence, "assessmentConfidence must not be null");
        measures = List.copyOf(Objects.requireNonNull(measures, "measures must not be null"));
        exposureDrivers = List.copyOf(Objects.requireNonNull(exposureDrivers, "exposureDrivers must not be null"));
        protectiveFactors = List.copyOf(Objects.requireNonNull(protectiveFactors, "protectiveFactors must not be null"));
        Objects.requireNonNull(assessmentSource, "assessmentSource must not be null");
        judgmentSchemaVersion = required(judgmentSchemaVersion, "judgmentSchemaVersion");
        decisionPolicyVersion = required(decisionPolicyVersion, "decisionPolicyVersion");
        int expectedMeasures = assessmentStatus == AssessmentStatus.MORE_INFORMATION_REQUIRED ? 0 : 4;
        if (measures.size() != expectedMeasures) {
            throw new IllegalArgumentException("context must contain the authoritative measure set for its status");
        }
    }

    public record MeasureContext(
            ProfessionalMeasure measure,
            double value,
            IndicatorBand band,
            double confidence) {
        public MeasureContext {
            Objects.requireNonNull(measure, "measure must not be null");
            Objects.requireNonNull(band, "band must not be null");
        }
    }

    public record FactorContext(
            String dimensionId,
            DecisionFactorEffect effect,
            ProfessionalFactorDirection direction) {
        public FactorContext {
            dimensionId = required(dimensionId, "dimensionId");
            Objects.requireNonNull(effect, "effect must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
