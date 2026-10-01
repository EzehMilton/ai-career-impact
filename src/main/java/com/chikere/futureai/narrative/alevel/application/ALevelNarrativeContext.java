package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelFactorDirection;
import com.chikere.futureai.decision.alevel.ALevelFactorEffect;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.OptionalityBand;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The complete, deliberately limited input to A-level narrative generation. */
public record ALevelNarrativeContext(
        List<String> selectedSubjects,
        Map<String, String> predictedGrades,
        List<String> strengthsAndInterests,
        List<String> preferredDegreeAreas,
        List<String> preferredCareerAreas,
        Optional<String> country,
        Optional<String> additionalContext,
        AssessmentStatus assessmentStatus,
        AssessmentConfidence assessmentConfidence,
        List<MeasureContext> measures,
        List<FactorContext> strengths,
        List<FactorContext> developmentOpportunities,
        AssessmentSource assessmentSource,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public ALevelNarrativeContext {
        selectedSubjects = List.copyOf(Objects.requireNonNull(selectedSubjects));
        predictedGrades = Map.copyOf(Objects.requireNonNull(predictedGrades));
        strengthsAndInterests = List.copyOf(Objects.requireNonNull(strengthsAndInterests));
        preferredDegreeAreas = List.copyOf(Objects.requireNonNull(preferredDegreeAreas));
        preferredCareerAreas = List.copyOf(Objects.requireNonNull(preferredCareerAreas));
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(additionalContext, "additionalContext must not be null");
        Objects.requireNonNull(assessmentStatus, "assessmentStatus must not be null");
        Objects.requireNonNull(assessmentConfidence, "assessmentConfidence must not be null");
        measures = List.copyOf(Objects.requireNonNull(measures));
        strengths = List.copyOf(Objects.requireNonNull(strengths));
        developmentOpportunities = List.copyOf(Objects.requireNonNull(developmentOpportunities));
        Objects.requireNonNull(assessmentSource, "assessmentSource must not be null");
        judgmentSchemaVersion = required(judgmentSchemaVersion, "judgmentSchemaVersion");
        decisionPolicyVersion = required(decisionPolicyVersion, "decisionPolicyVersion");
        int expectedMeasures = assessmentStatus == AssessmentStatus.MORE_INFORMATION_REQUIRED ? 0 : 6;
        if (measures.size() != expectedMeasures) {
            throw new IllegalArgumentException("context must contain the authoritative measure set for its status");
        }
    }

    public record MeasureContext(ALevelMeasure measure, double value, OptionalityBand band, double confidence) {
        public MeasureContext {
            Objects.requireNonNull(measure, "measure must not be null");
            Objects.requireNonNull(band, "band must not be null");
        }
    }

    public record FactorContext(String dimensionId, ALevelFactorEffect effect, ALevelFactorDirection direction) {
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
