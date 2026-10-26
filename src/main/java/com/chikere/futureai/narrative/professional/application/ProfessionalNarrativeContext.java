package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The complete, deliberately limited input to narrative generation: Java's decided measures and selected
 * factors, plus the work the person described. Confidence is deliberately absent while its semantics are
 * redesigned, so the model cannot reinterpret it.
 */
public record ProfessionalNarrativeContext(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsExperience,
        AssessmentStatus assessmentStatus,
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

    /**
     * One decided measure.
     *
     * @param name  the display name, for example "AI Exposure"
     * @param band  the display band, for example "Moderate", which is the only band vocabulary the model may use
     */
    public record MeasureContext(
            ProfessionalMeasure measure,
            String name,
            double score,
            String band) {
        public MeasureContext {
            Objects.requireNonNull(measure, "measure must not be null");
            name = required(name, "name");
            band = required(band, "band");
        }
    }

    /**
     * One factor Java selected for display, with what the model needs to explain it faithfully.
     *
     * @param dimension     the judgment's display name, for example "Digital executability"
     * @param description   the same sentence the visitor sees for this factor
     * @param rank          position within its list, 1 being the strongest
     * @param judgmentScore the decision model's 0-4 judgment for this dimension
     * @param signedEffect  the policy's signed contribution to AI Exposure; positive raises it, negative lowers it
     */
    public record FactorContext(
            String dimensionId,
            String dimension,
            String description,
            DecisionFactorEffect effect,
            ProfessionalFactorDirection direction,
            int rank,
            double judgmentScore,
            double signedEffect) {
        public FactorContext {
            dimensionId = required(dimensionId, "dimensionId");
            dimension = required(dimension, "dimension");
            description = required(description, "description");
            Objects.requireNonNull(effect, "effect must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be at least 1");
            }
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
