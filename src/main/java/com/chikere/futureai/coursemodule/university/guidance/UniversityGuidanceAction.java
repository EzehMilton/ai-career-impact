package com.chikere.futureai.coursemodule.university.guidance;

import java.util.List;
import java.util.Objects;

/**
 * One action-plan step. Its wording is a fixed template; whether it appears, and in what order, depends on the
 * evidence it cites.
 *
 * @param moduleIds modules named in or supporting the action; empty for course-wide actions
 * @param themes the skill priorities the action serves, if any
 * @param fallback the action met only its looser gate and was used to reach the minimum of two for its horizon
 */
public record UniversityGuidanceAction(
        String actionId,
        Horizon horizon,
        String ruleId,
        String text,
        List<Integer> moduleIds,
        List<UniversitySkillPriority.SkillTheme> themes,
        boolean fallback) {

    public enum Horizon {
        NEXT_30_DAYS,
        THIS_TERM,
        NEXT_6_TO_12_MONTHS
    }

    public UniversityGuidanceAction {
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(horizon, "horizon must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        moduleIds = List.copyOf(Objects.requireNonNull(moduleIds, "moduleIds must not be null"));
        themes = List.copyOf(Objects.requireNonNull(themes, "themes must not be null"));
    }
}
