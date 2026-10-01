package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An immutable record of one completed A-level assessment execution. */
public record StoredALevelAssessment(
        UUID id,
        Instant createdAt,
        ALevelAssessmentState state,
        AssessmentJudgmentSet judgments,
        ALevelAssessmentResult result,
        Optional<String> modelIdentifier) {

    public StoredALevelAssessment {
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
