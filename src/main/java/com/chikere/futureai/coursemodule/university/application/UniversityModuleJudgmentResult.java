package com.chikere.futureai.coursemodule.university.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Every judgment a provider returned for one module-analysis request: exactly one judgment set per selected module,
 * in request order. No classification, category, threshold or guidance is part of this result.
 */
public record UniversityModuleJudgmentResult(
        String schemaVersion,
        UniversityModuleJudgmentSource source,
        Optional<String> model,
        Optional<String> providerRequestId,
        OptionalLong inputTokens,
        OptionalLong outputTokens,
        List<UniversityModuleJudgmentSet> modules) {

    public UniversityModuleJudgmentResult {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        Objects.requireNonNull(source, "source must not be null");
        model = Objects.requireNonNull(model, "model must not be null").filter(value -> !value.isBlank());
        providerRequestId = Objects.requireNonNull(providerRequestId, "providerRequestId must not be null")
                .filter(value -> !value.isBlank());
        Objects.requireNonNull(inputTokens, "inputTokens must not be null");
        Objects.requireNonNull(outputTokens, "outputTokens must not be null");
        modules = List.copyOf(Objects.requireNonNull(modules, "modules must not be null"));
        if (modules.isEmpty() || modules.size() > UniversityModuleAnalysisRequest.MAXIMUM_MODULES) {
            throw new IllegalArgumentException("a result must hold between 1 and "
                    + UniversityModuleAnalysisRequest.MAXIMUM_MODULES + " modules");
        }
        for (int index = 0; index < modules.size(); index++) {
            if (modules.get(index).moduleIndex() != index + 1) {
                throw new IllegalArgumentException("module judgment sets must be in request order, one each");
            }
        }
    }

    /** Whether this result answers exactly the given request: the same modules, keys and titles, in order. */
    public boolean answers(UniversityModuleAnalysisRequest request) {
        var keyed = request.keyedModules();
        if (keyed.size() != modules.size()) {
            return false;
        }
        return modules.stream().allMatch(set -> set.moduleTitle().equals(keyed.get(set.moduleKey())));
    }
}
