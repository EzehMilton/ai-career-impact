package com.chikere.futureai.coursemodule.university.policy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Writes the Stage 5D calibration report from the frozen fixtures: target/university-module-classification/. */
class UniversityModuleClassificationReportTest {

    @Test
    void writesTheOfflineClassificationReportForAllFiftySixModules() throws Exception {
        String report = UniversityModuleClassificationReport.render(ModuleProbeFixtures.all(),
                new UniversityModulePolicyV1());
        Path file = Path.of("target", "university-module-classification", "report.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, StandardCharsets.UTF_8);

        assertThat(report).contains("**all 56**", "Close calls (internal QA)");
        assertThat(report.lines().filter(line -> line.startsWith("| ") && line.contains(" | m"))).hasSize(56);
    }
}
