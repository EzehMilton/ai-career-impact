package com.chikere.futureai.coursemodule.university.guidance;

import java.util.List;
import java.util.Objects;

/**
 * One course-level skill priority, from a pattern across the analysed modules.
 *
 * @param rank 1 for the strongest priority
 * @param strength the share of analysed modules supporting the theme, as the rule counts them (0-1)
 * @param relaxed the theme met only the relaxed one-module bar, to bring the list up to the minimum of three
 */
public record UniversitySkillPriority(
        SkillTheme theme,
        String ruleId,
        String title,
        String explanation,
        List<Integer> moduleIds,
        double strength,
        int rank,
        boolean relaxed) {

    public enum SkillTheme {
        CRITICAL_EVALUATION("Critical evaluation and independent reasoning"),
        PRACTICAL_APPLICATION("Practical application"),
        TECHNICAL_FOUNDATIONS("Technical and quantitative foundations"),
        COMMUNICATION_AND_COLLABORATION("Communication and collaboration"),
        PROFESSIONAL_JUDGEMENT("Professional judgement and standards"),
        INDEPENDENT_WORK("Independent work and evidence of your own capability"),
        AI_LITERACY("Using AI tools critically and responsibly");

        private final String title;

        SkillTheme(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public UniversitySkillPriority {
        Objects.requireNonNull(theme, "theme must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        moduleIds = List.copyOf(Objects.requireNonNull(moduleIds, "moduleIds must not be null"));
        if (moduleIds.isEmpty()) {
            throw new IllegalArgumentException("a skill priority must cite at least one module");
        }
        if (!Double.isFinite(strength) || strength < 0 || strength > 1) {
            throw new IllegalArgumentException("strength must be between 0 and 1");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1");
        }
    }
}
