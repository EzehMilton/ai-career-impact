package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An immutable record of one completed assessment execution. */
public record StoredProfessionalAssessment(
        UUID id,
        Instant createdAt,
        ProfessionalAssessmentState state,
        AssessmentJudgmentSet judgments,
        ProfessionalAssessmentResult result,
        Optional<String> modelIdentifier,
        ProfessionalAssessmentInputMetadata inputMetadata) {

    public StoredProfessionalAssessment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(modelIdentifier, "modelIdentifier must not be null");
        Objects.requireNonNull(inputMetadata, "inputMetadata must not be null");
        if (!judgments.schemaVersion().equals(result.judgmentSchemaVersion())) {
            throw new IllegalArgumentException("judgment schema versions must match");
        }
    }

    public StoredProfessionalAssessment(UUID id, Instant createdAt, ProfessionalAssessmentState state,
            AssessmentJudgmentSet judgments, ProfessionalAssessmentResult result,
            Optional<String> modelIdentifier) {
        this(id, createdAt, state, judgments, result, modelIdentifier,
                ProfessionalAssessmentInputMetadata.manual());
    }
}
