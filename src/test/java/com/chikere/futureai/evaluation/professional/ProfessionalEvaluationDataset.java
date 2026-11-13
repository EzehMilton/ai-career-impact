package com.chikere.futureai.evaluation.professional;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    static final Set<String> REQUIRED_CALIBRATION_CASES = Set.of(
            "insufficient_information",
            "ambiguous_hybrid_operations_role",
            "contradictory_evidence_electrician",
            "classification_boundary_digital_adviser");

    private ProfessionalEvaluationDataset() {
    }

    static List<ProfessionalEvaluationCase> load() {
        try (InputStream input = ProfessionalEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Evaluation dataset not found: " + RESOURCE);
            }
            List<ProfessionalEvaluationCase> cases = List.copyOf(Arrays.asList(
                    JsonMapper.builder().build().readValue(input, ProfessionalEvaluationCase[].class)));
            validate(cases, true);
            return cases;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not read evaluation dataset: " + RESOURCE, exception);
        }
    }

    static List<ProfessionalEvaluationCase> load(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try (InputStream input = Files.newInputStream(path)) {
            List<ProfessionalEvaluationCase> cases = List.copyOf(Arrays.asList(
                    JsonMapper.builder().build().readValue(input, ProfessionalEvaluationCase[].class)));
            validate(cases, false);
            return cases;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not read professional evaluation dataset: " + path, exception);
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
                        "Unknown professional evaluation case '" + requestedId + "'. Available cases: "
                                + cases.stream().map(ProfessionalEvaluationCase::id).sorted().toList()));
    }

    private static void validate(List<ProfessionalEvaluationCase> cases, boolean requireBuiltInCoverage) {
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
        if (requireBuiltInCoverage) COMPARISON_PAIRS.forEach(pair -> {
            if (!ids.contains(pair.firstId()) || !ids.contains(pair.secondId())) {
                throw new IllegalStateException("Comparison pair references an unknown case: " + pair);
            }
        });
        if (requireBuiltInCoverage && !ids.containsAll(REQUIRED_CALIBRATION_CASES)) {
            throw new IllegalStateException("Evaluation dataset is missing required calibration cases: "
                    + REQUIRED_CALIBRATION_CASES.stream().filter(id -> !ids.contains(id)).sorted().toList());
        }
    }

    record ComparisonPair(String firstId, String secondId) {
    }
}
