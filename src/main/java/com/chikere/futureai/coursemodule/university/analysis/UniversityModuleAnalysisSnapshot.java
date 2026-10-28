package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The one immutable, completed module analysis of a paid University assessment: the exact course context and
 * modules sent, every judgment returned and the deterministic classification made at the time. Nothing in it is
 * recomputed later, and it never changes the course-level assessment it belongs to.
 */
public record UniversityModuleAnalysisSnapshot(
        UUID snapshotId,
        UUID assessmentId,
        String schemaVersion,
        String policyVersion,
        UniversityModuleJudgmentSource judgmentSource,
        Optional<String> model,
        Optional<String> providerRequestId,
        OptionalLong inputTokens,
        OptionalLong outputTokens,
        String courseTitle,
        String courseDescription,
        List<String> skillsDeveloped,
        List<UniversityModuleAnalysisItem> items,
        Instant createdAt,
        Instant completedAt) {

    /** Only completed snapshots exist; a failed attempt is a {@link UniversityModuleAnalysisFailure}. */
    public static final String STATUS = "COMPLETED";

    public UniversityModuleAnalysisSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(judgmentSource, "judgmentSource must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(providerRequestId, "providerRequestId must not be null");
        Objects.requireNonNull(inputTokens, "inputTokens must not be null");
        Objects.requireNonNull(outputTokens, "outputTokens must not be null");
        Objects.requireNonNull(courseTitle, "courseTitle must not be null");
        Objects.requireNonNull(courseDescription, "courseDescription must not be null");
        skillsDeveloped = List.copyOf(Objects.requireNonNull(skillsDeveloped, "skillsDeveloped must not be null"));
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (items.isEmpty() || items.size() > UniversityModuleAnalysisRequest.MAXIMUM_MODULES) {
            throw new IllegalArgumentException("a snapshot must hold between 1 and "
                    + UniversityModuleAnalysisRequest.MAXIMUM_MODULES + " modules");
        }
        for (int index = 0; index < items.size(); index++) {
            UniversityModuleAnalysisItem item = items.get(index);
            if (item.judgments().moduleIndex() != index + 1) {
                throw new IllegalArgumentException("items must be in request order, one per module");
            }
            if (index > 0 && item.moduleId() <= items.get(index - 1).moduleId()) {
                throw new IllegalArgumentException("items must be in course order with distinct module ids");
            }
        }
    }

    /** The course positions of the analysed modules, in course order. */
    public List<Integer> selectedModuleIds() {
        return items.stream().map(UniversityModuleAnalysisItem::moduleId).toList();
    }
}
