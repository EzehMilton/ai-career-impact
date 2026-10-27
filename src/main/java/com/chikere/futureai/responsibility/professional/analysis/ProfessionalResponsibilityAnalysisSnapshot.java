package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The immutable paid responsibility analysis of one Professional assessment: an additional artefact, never a
 * replacement for the role-level assessment, and never fed back into its scores, bands or factors.
 *
 * <p>It names the schema and policy versions that produced it, so a later v2 can never be mistaken for it, and it
 * holds everything needed to explain itself without the provider or the policy code that made it.</p>
 */
public record ProfessionalResponsibilityAnalysisSnapshot(
        UUID snapshotId,
        UUID assessmentId,
        ResponsibilityAnalysisStatus status,
        String schemaVersion,
        String policyVersion,
        ResponsibilityJudgmentSource judgmentSource,
        Optional<String> model,
        Optional<String> providerRequestId,
        OptionalLong inputTokens,
        OptionalLong outputTokens,
        ProfessionalResponsibilityAnalysisInput input,
        ResponsibilityScoredJudgment regulatedContext,
        ResponsibilityLevel regulatedContextLevel,
        Map<String, Double> policyParameters,
        List<ResponsibilityAnalysisItem> items,
        Instant createdAt,
        Instant completedAt) {

    public ProfessionalResponsibilityAnalysisSnapshot {
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(assessmentId);
        if (status != ResponsibilityAnalysisStatus.COMPLETED) {
            throw new IllegalArgumentException("only a completed analysis is a snapshot");
        }
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(judgmentSource);
        Objects.requireNonNull(model);
        Objects.requireNonNull(providerRequestId);
        Objects.requireNonNull(inputTokens);
        Objects.requireNonNull(outputTokens);
        Objects.requireNonNull(input);
        Objects.requireNonNull(regulatedContext);
        Objects.requireNonNull(regulatedContextLevel);
        policyParameters = Map.copyOf(policyParameters);
        items = List.copyOf(items);
        if (items.size() != input.responsibilities().size()) {
            throw new IllegalArgumentException("a snapshot holds exactly one item per selected responsibility");
        }
        for (int index = 0; index < items.size(); index++) {
            ResponsibilityAnalysisItem item = items.get(index);
            if (item.responsibilityIndex() != index + 1
                    || !item.responsibilityText().equals(input.responsibilities().get(index))) {
                throw new IllegalArgumentException("snapshot items must match the selected responsibilities in order");
            }
        }
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(completedAt);
    }
}
