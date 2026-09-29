package com.chikere.futureai.evaluation.professional;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ProfessionalEvaluationDataset {

    static final String RESOURCE = "/evaluations/professional-v1-cases.json";

    static final List<ComparisonPair> COMPARISON_PAIRS = List.of(
            new ComparisonPair("routine_software_developer", "senior_software_architect"),
            new ComparisonPair("transactional_accountant", "advisory_management_accountant"),
            new ComparisonPair("graphic_production_designer", "creative_director"));

    private ProfessionalEvaluationDataset() {
    }

    static List<ProfessionalEvaluationCase> load() {
        try (InputStream input = ProfessionalEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Evaluation dataset not found: " + RESOURCE);
            }
            List<ProfessionalEvaluationCase> cases = List.copyOf(Arrays.asList(
                    JsonMapper.builder().build().readValue(input, ProfessionalEvaluationCase[].class)));
            validate(cases);
            return cases;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not read evaluation dataset: " + RESOURCE, exception);
        }
    }

    static List<ProfessionalEvaluationCase> select(List<ProfessionalEvaluationCase> cases, String requestedId) {
        Objects.requireNonNull(cases, "cases must not be null");
        if (requestedId == null || requestedId.isBlank()) {
            return List.copyOf(cases);
        }
        return cases.stream()
                .filter(evaluationCase -> evaluationCase.id().equals(requestedId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown jev.eval.case '" + requestedId + "'. Available cases: "
                                + cases.stream().map(ProfessionalEvaluationCase::id).sorted().toList()));
    }

    private static void validate(List<ProfessionalEvaluationCase> cases) {
        if (cases.isEmpty()) {
            throw new IllegalStateException("Evaluation dataset must not be empty");
        }
        Set<String> ids = new HashSet<>();
        cases.forEach(evaluationCase -> {
            if (!ids.add(evaluationCase.id())) {
                throw new IllegalStateException("Duplicate evaluation case ID: " + evaluationCase.id());
            }
            evaluationCase.toAssessmentState();
        });
        COMPARISON_PAIRS.forEach(pair -> {
            if (!ids.contains(pair.firstId()) || !ids.contains(pair.secondId())) {
                throw new IllegalStateException("Comparison pair references an unknown case: " + pair);
            }
        });
    }

    record ComparisonPair(String firstId, String secondId) {
    }
}
