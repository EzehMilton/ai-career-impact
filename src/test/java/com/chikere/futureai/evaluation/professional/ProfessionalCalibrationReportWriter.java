package com.chikere.futureai.evaluation.professional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ProfessionalCalibrationReportWriter {

    private ProfessionalCalibrationReportWriter() {}

    static void write(List<ProfessionalCalibrationComparison.CaseComparison> comparisons, Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, render(comparisons), StandardCharsets.UTF_8);
        }
        catch (IOException failure) {
            throw new IllegalStateException("Could not write professional provider calibration report", failure);
        }
    }

    static String render(List<ProfessionalCalibrationComparison.CaseComparison> comparisons) {
        if (comparisons.isEmpty()) throw new IllegalArgumentException("comparisons must not be empty");
        StringBuilder report = new StringBuilder("# Professional Jev/OpenAI calibration\n\n")
                .append("- Schema: `professional-v1`\n")
                .append("- Deterministic policy: `professional-decision-v2`\n")
                .append("- Cases: ").append(comparisons.size()).append("\n")
                .append("- Comparison rule: probabilities, confidence values and expected scores are provider-native ")
                .append("and are not expected to match. Materiality is assessed from final customer-facing outcomes.\n\n");
        long affected = comparisons.stream().filter(value -> !value.materialDifferences().isEmpty()).count();
        report.append("## Summary\n\n- Cases with material customer-facing differences: ")
                .append(affected).append(" / ").append(comparisons.size()).append("\n\n");
        for (var comparison : comparisons) {
            report.append("## `").append(comparison.caseId()).append("`\n\n");
            if (comparison.materialDifferences().isEmpty()) {
                report.append("No configured material outcome difference.\n\n");
                continue;
            }
            report.append("| Field | Jev | OpenAI | Why material |\n|---|---|---|---|\n");
            comparison.materialDifferences().forEach(difference -> report.append("| ")
                    .append(escape(difference.field())).append(" | ")
                    .append(escape(difference.jevValue())).append(" | ")
                    .append(escape(difference.openAiValue())).append(" | ")
                    .append(escape(difference.reason())).append(" |\n"));
            report.append("\n");
        }
        return report.toString();
    }

    private static String escape(String value) { return value.replace("|", "\\|").replace("\n", " "); }
}
