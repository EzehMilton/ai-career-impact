package com.chikere.futureai.responsibility.professional.guidance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable guidance of one paid Professional assessment, tied to the responsibility analysis it was derived
 * from and the policy version that produced it. Never recomputed once stored.
 */
public record ProfessionalResponsibilityGuidanceSnapshot(
        UUID guidanceId,
        UUID assessmentId,
        UUID analysisSnapshotId,
        String policyVersion,
        Instant createdAt,
        ProfessionalResponsibilityGuidance guidance) {

    public ProfessionalResponsibilityGuidanceSnapshot {
        Objects.requireNonNull(guidanceId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(analysisSnapshotId);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(guidance);
        if (!guidance.analysisSnapshotId().equals(analysisSnapshotId) || !guidance.policyVersion().equals(policyVersion)) {
            throw new IllegalArgumentException("the guidance must belong to the snapshot's analysis and policy version");
        }
    }
}
