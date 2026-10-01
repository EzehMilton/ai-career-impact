package com.chikere.futureai.narrative.university.application;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityFactorDirection;
import com.chikere.futureai.decision.university.UniversityFactorEffect;
import com.chikere.futureai.decision.university.UniversityMeasure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The complete, deliberately limited input to university narrative generation. */
public record UniversityNarrativeContext(
        String courseTitle,
        String courseDescription,
        List<String> keyModules,
        List<String> skillsDeveloped,
        List<String> preferredCareerAreas,
        Optional<String> institution,
        Optional<String> country,
        AssessmentStatus assessmentStatus,
        AssessmentConfidence assessmentConfidence,
        List<MeasureContext> measures,
        List<FactorContext> strengths,
        List<FactorContext> developmentOpportunities,
        AssessmentSource assessmentSource,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public UniversityNarrativeContext {
        courseTitle = required(courseTitle, "courseTitle");
        courseDescription = required(courseDescription, "courseDescription");
        keyModules = List.copyOf(Objects.requireNonNull(keyModules, "keyModules must not be null"));
        skillsDeveloped = List.copyOf(Objects.requireNonNull(skillsDeveloped, "skillsDeveloped must not be null"));
        preferredCareerAreas = List.copyOf(
                Objects.requireNonNull(preferredCareerAreas, "preferredCareerAreas must not be null"));
        Objects.requireNonNull(institution, "institution must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(assessmentStatus, "assessmentStatus must not be null");
        Objects.requireNonNull(assessmentConfidence, "assessmentConfidence must not be null");
        measures = List.copyOf(Objects.requireNonNull(measures, "measures must not be null"));
        strengths = List.copyOf(Objects.requireNonNull(strengths, "strengths must not be null"));
        developmentOpportunities = List.copyOf(Objects.requireNonNull(
                developmentOpportunities, "developmentOpportunities must not be null"));
        Objects.requireNonNull(assessmentSource, "assessmentSource must not be null");
        judgmentSchemaVersion = required(judgmentSchemaVersion, "judgmentSchemaVersion");
        decisionPolicyVersion = required(decisionPolicyVersion, "decisionPolicyVersion");
        int expectedMeasures = assessmentStatus == AssessmentStatus.MORE_INFORMATION_REQUIRED ? 0 : 5;
        if (measures.size() != expectedMeasures) {
            throw new IllegalArgumentException("context must contain the authoritative measure set for its status");
        }
    }

    public record MeasureContext(UniversityMeasure measure, double value, ReadinessBand band, double confidence) {
        public MeasureContext {
            Objects.requireNonNull(measure, "measure must not be null");
            Objects.requireNonNull(band, "band must not be null");
        }
    }

    public record FactorContext(String dimensionId, UniversityFactorEffect effect,
            UniversityFactorDirection direction) {
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
