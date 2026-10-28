package com.chikere.futureai.coursemodule.university.policy;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage 5D offline calibration: every frozen live module through {@link UniversityModulePolicyV1}, as a readable
 * table with category counts. No provider call and nothing persisted.
 */
final class UniversityModuleClassificationReport {

    private UniversityModuleClassificationReport() {
    }

    static String render(Map<String, UniversityModuleJudgmentResult> courses, UniversityModulePolicyV1 policy) {
        StringBuilder out = new StringBuilder("# University module classification (" + UniversityModulePolicyV1.POLICY_VERSION
                + ", offline)\n\n")
                .append("HIGH from ").append(UniversityModulePolicyV1.HIGH_FROM).append(", dominance margin ")
                .append(UniversityModulePolicyV1.DOMINANCE_MARGIN).append(", close-call band ")
                .append(UniversityModulePolicyV1.CLOSE_CALL_BAND).append(", integrity flag AI >= ")
                .append(UniversityModulePolicyV1.INTEGRITY_AI_FROM).append(" and independent >= ")
                .append(UniversityModulePolicyV1.INTEGRITY_INDEPENDENT_FROM).append(", professional standards >= ")
                .append(UniversityModulePolicyV1.PROFESSIONAL_STANDARDS_FROM).append(".\n\n")
                .append("| Course | Id | Module | Category | Rule | AI | Indep. | Judg. | Interp. | Pract. | Tech. | "
                        + "Prof. | Flags | Close call |\n|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|\n");
        Map<UniversityModuleCategory, Integer> overall = new EnumMap<>(UniversityModuleCategory.class);
        Map<String, Map<UniversityModuleCategory, Integer>> byCourse = new LinkedHashMap<>();
        int integrity = 0, professional = 0, close = 0, total = 0;
        for (var course : courses.entrySet()) {
            Map<UniversityModuleCategory, Integer> counts = new EnumMap<>(UniversityModuleCategory.class);
            for (UniversityModuleClassification value : policy.classify(course.getValue())) {
                var in = value.inputs();
                out.append("| ").append(course.getKey()).append(" | ").append(value.moduleKey()).append(" | ")
                        .append(value.moduleTitle()).append(" | ").append(value.category()).append(" | ")
                        .append(value.rule().name().substring(0, value.rule().name().indexOf('_'))).append(" | ")
                        .append(f(in.get("ai_learning_assistance"))).append(" | ")
                        .append(f(in.get("independent_demonstration"))).append(" | ")
                        .append(f(in.get("human_judgement"))).append(" | ").append(f(in.get("interpersonal_work")))
                        .append(" | ").append(f(in.get("practical_application"))).append(" | ")
                        .append(f(in.get("technical_quantitative"))).append(" | ")
                        .append(f(in.get("professional_standards"))).append(" | ")
                        .append(String.join(", ", value.flags().stream().map(flag -> flag.name().startsWith("INDEP")
                                ? "INTEGRITY" : "PROFESSIONAL").toList()))
                        .append(" | ").append(String.join("; ", value.closeCallReasons())).append(" |\n");
                counts.merge(value.category(), 1, Integer::sum);
                overall.merge(value.category(), 1, Integer::sum);
                integrity += value.hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE) ? 1 : 0;
                professional += value.hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT) ? 1 : 0;
                close += value.closeCall() ? 1 : 0;
                total++;
            }
            byCourse.put(course.getKey(), counts);
        }
        out.append("\n## Categories by course\n\n| Course |");
        List<UniversityModuleCategory> categories = List.of(UniversityModuleCategory.values());
        categories.forEach(category -> out.append(' ').append(category).append(" |"));
        out.append("\n|---|").append("---:|".repeat(categories.size())).append('\n');
        byCourse.forEach((course, counts) -> {
            out.append("| ").append(course).append(" |");
            categories.forEach(category -> out.append(' ').append(counts.getOrDefault(category, 0)).append(" |"));
            out.append('\n');
        });
        out.append("| **all ").append(total).append("** |");
        categories.forEach(category -> out.append(' ').append(overall.getOrDefault(category, 0)).append(" |"));
        out.append("\n\n- Independent-demonstration sensitive: ").append(integrity).append(" of ").append(total)
                .append("\n- Professional standards relevant: ").append(professional).append(" of ").append(total)
                .append("\n- Close calls (internal QA): ").append(close).append(" of ").append(total).append('\n');
        return out.toString();
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
