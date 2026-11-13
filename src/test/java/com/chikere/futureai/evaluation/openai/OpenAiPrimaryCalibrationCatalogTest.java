package com.chikere.futureai.evaluation.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPrimaryCalibrationCatalogTest {
    private static final Set<String> REQUIRED = Set.of("CLEAR_LOW", "CLEAR_MODERATE", "CLEAR_HIGH", "BOUNDARY",
            "MISSING_INFORMATION", "AMBIGUOUS", "CONTRADICTORY", "REPEATED_IDENTICAL", "SEMANTIC_VARIANT");

    @Test
    void catalogCoversOpenAiMeritCasesForBothPrimaryJourneys() throws IOException {
        JsonNode root;
        try (var input = getClass().getResourceAsStream("/evaluations/openai-primary-calibration-v1.json")) {
            root = new ObjectMapper().readTree(input);
        }
        assertThat(root.path("version").asText()).isEqualTo("openai-primary-calibration-v1");
        Set<String> ids = new HashSet<>();
        root.path("cases").forEach(node ->
                assertThat(ids.add(node.path("id").asText())).as("unique calibration id").isTrue());
        for (String journey : Set.of("UNIVERSITY", "A_LEVEL")) {
            Set<String> categories = new HashSet<>();
            root.path("cases").forEach(node -> {
                if (journey.equals(node.path("journey").asText())) categories.add(node.path("category").asText());
            });
            assertThat(categories).containsAll(REQUIRED);
        }
        assertThat(root.path("cases").findValuesAsText("pair"))
                .contains("university-repeat", "university-variant", "alevel-repeat", "alevel-variant");
    }
}
