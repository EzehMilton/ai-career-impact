package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An immutable record of one completed university assessment execution. */
public record StoredUniversityAssessment(
        UUID id,
        Instant createdAt,
        UniversityAssessmentState state,
        AssessmentJudgmentSet judgments,
        UniversityAssessmentResult result,
        Optional<String> modelIdentifier) {

    public StoredUniversityAssessment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(modelIdentifier, "modelIdentifier must not be null");
        if (!judgments.schemaVersion().equals(result.judgmentSchemaVersion())) {
            throw new IllegalArgumentException("judgment schema versions must match");
        }
    }
}
