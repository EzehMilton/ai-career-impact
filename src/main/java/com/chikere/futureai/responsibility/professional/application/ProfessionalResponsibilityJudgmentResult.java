package com.chikere.futureai.responsibility.professional.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Every judgment a provider returned for one responsibility-analysis request: one role-level regulated-context
 * judgment and exactly one judgment set per selected responsibility, in request order. No classification,
 * category, threshold or guidance is part of this result.
 */
public record ProfessionalResponsibilityJudgmentResult(
        String schemaVersion,
        ResponsibilityJudgmentSource source,
        Optional<String> model,
        Optional<String> providerRequestId,
        OptionalLong inputTokens,
        OptionalLong outputTokens,
        ResponsibilityScoredJudgment regulatedContext,
        List<ResponsibilityJudgmentSet> responsibilities) {

    public ProfessionalResponsibilityJudgmentResult {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        Objects.requireNonNull(source, "source must not be null");
        model = Objects.requireNonNull(model, "model must not be null").filter(value -> !value.isBlank());
        providerRequestId = Objects.requireNonNull(providerRequestId, "providerRequestId must not be null")
                .filter(value -> !value.isBlank());
        Objects.requireNonNull(inputTokens, "inputTokens must not be null");
        Objects.requireNonNull(outputTokens, "outputTokens must not be null");
        Objects.requireNonNull(regulatedContext, "regulatedContext must not be null");
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities, "responsibilities must not be null"));
        if (responsibilities.isEmpty()
                || responsibilities.size() > ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES) {
            throw new IllegalArgumentException("a result must hold between 1 and "
                    + ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES + " responsibilities");
        }
        for (int index = 0; index < responsibilities.size(); index++) {
            if (responsibilities.get(index).responsibilityIndex() != index + 1) {
                throw new IllegalArgumentException("responsibility judgment sets must be in request order, one each");
            }
        }
    }

    /**
     * Whether this result answers exactly the given request: the same number of responsibilities, with the same
     * keys and texts in the same order.
     */
    public boolean answers(ProfessionalResponsibilityAnalysisRequest request) {
        var keyed = request.keyedResponsibilities();
        if (keyed.size() != responsibilities.size()) {
            return false;
        }
        return responsibilities.stream().allMatch(set ->
                set.responsibilityText().equals(keyed.get(set.responsibilityKey())));
    }
}
