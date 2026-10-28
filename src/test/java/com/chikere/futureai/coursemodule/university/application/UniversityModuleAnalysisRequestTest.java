package com.chikere.futureai.coursemodule.university.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityModuleAnalysisRequestTest {

    @Test
    void modulesGetStableKeysInTheOrderSupplied() {
        var request = request(List.of(" Thermodynamics ", "Fluid Mechanics", "Individual Project"));
        assertThat(request.keyedModules()).containsExactly(
                java.util.Map.entry("m1", "Thermodynamics"), java.util.Map.entry("m2", "Fluid Mechanics"),
                java.util.Map.entry("m3", "Individual Project"));
        assertThat(UniversityModuleAnalysisRequest.key(8)).isEqualTo("m8");
        assertThatThrownBy(() -> UniversityModuleAnalysisRequest.key(9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eightModulesAreAcceptedAndMoreAreRefusedRatherThanTruncated() {
        assertThat(request(IntStream.rangeClosed(1, 8).mapToObj(i -> "Module " + i).toList()).modules()).hasSize(8);
        assertThatThrownBy(() -> request(IntStream.rangeClosed(1, 9).mapToObj(i -> "Module " + i).toList()))
                .hasMessageContaining("at most 8").hasMessageContaining("caller's decision");
    }

    @Test
    void emptyBlankDuplicateAndOverlongModulesAreRefused() {
        assertThatThrownBy(() -> request(List.of())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> request(List.of("Typography", " "))).hasMessageContaining("blank");
        assertThatThrownBy(() -> request(List.of("Typography", "Typography "))).hasMessageContaining("distinct");
        assertThatThrownBy(() -> request(List.of("x".repeat(201)))).hasMessageContaining("200");
    }

    @Test
    void theRequestHasNoPlaceForPreferredCareerAreas() {
        assertThat(UniversityModuleAnalysisRequest.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("assessmentId", "courseTitle", "courseDescription", "modules", "skillsDeveloped");
    }

    private static UniversityModuleAnalysisRequest request(List<String> modules) {
        return new UniversityModuleAnalysisRequest(UUID.randomUUID(), "BEng Mechanical Engineering",
                "An engineering degree.", modules, List.of("Problem solving"));
    }
}
