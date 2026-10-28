package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stage 5F offline calibration: the guidance for every frozen course, readable, with section counts. */
final class UniversityGuidanceCalibrationReport {

    private UniversityGuidanceCalibrationReport() {
    }

    static String render(Map<String, UniversityModuleAnalysisSnapshot> courses, UniversityGuidancePolicyV1 policy) {
        StringBuilder out = new StringBuilder("# University guidance calibration (" + UniversityGuidancePolicyV1.POLICY_VERSION
                + ", offline)\n\n| Course | AI opps | Own capability | Technical | Safeguards | Skills | 30 days | "
                + "Term | 6-12 months |\n|---|---:|---:|---:|---:|---|---:|---:|---:|\n");
        Map<String, UniversityGuidance> guidance = new LinkedHashMap<>();
        Map<String, Integer> actionUse = new LinkedHashMap<>();
        courses.forEach((course, snapshot) -> guidance.put(course, policy.decide(snapshot)));
        guidance.forEach((course, value) -> {
            out.append("| ").append(course).append(" | ").append(value.aiLearningOpportunities().size()).append(" | ")
                    .append(value.ownCapabilityAreas().size()).append(" | ").append(value.technicalFoundations().size())
                    .append(" | ").append(value.safeguards().size()).append(" | ")
                    .append(String.join(", ", value.skillPriorities().stream().map(skill -> skill.theme().name()
                            + (skill.relaxed() ? "*" : "")).toList()))
                    .append(" | ").append(value.next30Days().size()).append(" | ").append(value.thisTerm().size())
                    .append(" | ").append(value.next6To12Months().size()).append(" |\n");
            for (var action : actions(value)) actionUse.merge(action.ruleId(), 1, Integer::sum);
        });
        out.append("\n`*` = theme relaxed in to reach three priorities.\n\n## Action rules used across the seven courses\n\n");
        actionUse.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey())).forEach(entry -> out.append("- ").append(entry.getKey())
                .append(": ").append(entry.getValue()).append(" of ").append(courses.size()).append('\n'));
        long fallbacks = guidance.values().stream().flatMap(value -> actions(value).stream())
                .filter(UniversityGuidanceAction::fallback).count();
        out.append("\nFallback actions used: ").append(fallbacks).append('\n');
        guidance.forEach((course, value) -> actions(value).stream().filter(UniversityGuidanceAction::fallback)
                .forEach(action -> out.append("- ").append(course).append(": ").append(action.ruleId()).append('\n')));
        out.append("\n## First action at each horizon\n\n| Course | 30 days | Term | 6-12 months |\n|---|---|---|---|\n");
        guidance.forEach((course, value) -> out.append("| ").append(course).append(" | ")
                .append(value.next30Days().getFirst().ruleId()).append(" | ").append(value.thisTerm().getFirst().ruleId())
                .append(" | ").append(value.next6To12Months().getFirst().ruleId()).append(" |\n"));
        guidance.forEach((course, value) -> {
            out.append("\n## ").append(course).append("\n\n### AI learning opportunities\n");
            items(out, value.aiLearningOpportunities());
            out.append("\n### Where your own capability matters most\n");
            items(out, value.ownCapabilityAreas());
            out.append("\n### Technical and analytical foundations\n");
            items(out, value.technicalFoundations());
            out.append("\n### Safeguards\n");
            items(out, value.safeguards());
            out.append("\n### Skill priorities\n");
            value.skillPriorities().forEach(skill -> out.append("- ").append(skill.rank()).append(". **")
                    .append(skill.title()).append("** (").append(skill.ruleId()).append(", strength ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", skill.strength())).append(", modules ")
                    .append(skill.moduleIds()).append(skill.relaxed() ? ", relaxed" : "").append("): ")
                    .append(skill.explanation()).append('\n'));
            out.append("\n### Next 30 days\n");
            value.next30Days().forEach(action -> action(out, action));
            out.append("\n### This term\n");
            value.thisTerm().forEach(action -> action(out, action));
            out.append("\n### Next 6-12 months\n");
            value.next6To12Months().forEach(action -> action(out, action));
        });
        return out.toString();
    }

    private static List<UniversityGuidanceAction> actions(UniversityGuidance guidance) {
        return java.util.stream.Stream.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months())
                .flatMap(List::stream).toList();
    }

    private static void items(StringBuilder out, List<UniversityGuidanceItem> items) {
        if (items.isEmpty()) out.append("- none\n");
        items.forEach(item -> out.append("- **").append(item.title()).append("** (").append(item.ruleId())
                .append(", modules ").append(item.moduleIds()).append("): ").append(item.text()).append('\n'));
    }

    private static void action(StringBuilder out, UniversityGuidanceAction action) {
        out.append("- ").append(action.text()).append(" (").append(action.ruleId()).append(", themes ")
                .append(action.themes()).append(action.fallback() ? ", fallback" : "").append(")\n");
    }
}
