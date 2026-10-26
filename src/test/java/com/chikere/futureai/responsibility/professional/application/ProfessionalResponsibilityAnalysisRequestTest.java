package com.chikere.futureai.responsibility.professional.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProfessionalResponsibilityAnalysisRequestTest {

    @Test
    void oneToEightSelectedResponsibilitiesGetStableKeysInTheOrderSupplied() {
        var request = request(List.of("Write reports", "Meet clients", "Review contracts"));

        assertThat(request.keyedResponsibilities()).containsExactly(
                java.util.Map.entry("r1", "Write reports"), java.util.Map.entry("r2", "Meet clients"),
                java.util.Map.entry("r3", "Review contracts"));
        assertThat(request(numbered(8)).keyedResponsibilities()).hasSize(8).containsKeys("r1", "r8");
        assertThat(ProfessionalResponsibilityAnalysisRequest.key(1)).isEqualTo("r1");
        assertThatIllegalArgumentException().isThrownBy(() -> ProfessionalResponsibilityAnalysisRequest.key(9));
    }

    @Test
    void moreThanEightIsRefusedRatherThanTruncatedAndEmptyIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> request(numbered(9)))
                .withMessageContaining("at most 8").withMessageContaining("9 were supplied")
                .withMessageContaining("caller's decision");
        assertThatIllegalArgumentException().isThrownBy(() -> request(List.of()))
                .withMessageContaining("at least one");
    }

    @Test
    void blankDuplicateOrOverlongResponsibilitiesAndMissingContextAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> request(List.of("Write reports", " ")));
        assertThatIllegalArgumentException().isThrownBy(() -> request(List.of("Write reports", "Write reports")));
        assertThatIllegalArgumentException().isThrownBy(() -> request(List.of("x".repeat(301))));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProfessionalResponsibilityAnalysisRequest(
                UUID.randomUUID(), " ", "Work", List.of("Write reports"), List.of()));
        assertThat(request(List.of("  Write reports  ")).responsibilities()).containsExactly("Write reports");
    }

    @Test
    void theApplicationLayerDependsOnNoProviderSdk() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/chikere/futureai/responsibility/professional/application"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString())
                        .doesNotContain("org.springaicommunity", "org.springframework.ai", "org.springframework.web");
            }
        }
    }

    static ProfessionalResponsibilityAnalysisRequest request(List<String> responsibilities) {
        return new ProfessionalResponsibilityAnalysisRequest(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Test role", "Work described for the test.", responsibilities, List.of("Communication"));
    }

    private static List<String> numbered(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Responsibility " + index).toList();
    }
}
