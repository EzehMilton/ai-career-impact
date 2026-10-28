package com.chikere.futureai.narrative.university.paid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything the paid narrative may see, already decided: the course, the three measures with their bands and
 * deterministic drivers and limits, the analysed modules with their classifications, and the titles of the skill
 * priorities, safeguards and action themes. No probability distribution, rule id or diagnostic is included.
 *
 * @param professionalStandardsRelevant whether any analysed module carries the professional-standards safeguard, so
 *     the narrative may mention professional or regulated standards only when the data says so
 */
public record UniversityPaidNarrativeContext(
        UUID assessmentId,
        UUID moduleAnalysisId,
        String courseTitle,
        List<Measure> measures,
        List<Module> modules,
        List<String> skillPriorities,
        List<String> safeguards,
        List<String> actionThemes,
        boolean professionalStandardsRelevant) {

    public record Measure(String name, String band, List<String> strengthenedBy, List<String> heldBackBy) {
        public Measure {
            Objects.requireNonNull(name);
            Objects.requireNonNull(band);
            strengthenedBy = List.copyOf(strengthenedBy);
            heldBackBy = List.copyOf(heldBackBy);
        }
    }

    public record Module(String title, String classification) {
        public Module {
            Objects.requireNonNull(title);
            Objects.requireNonNull(classification);
        }
    }

    public UniversityPaidNarrativeContext {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(moduleAnalysisId);
        Objects.requireNonNull(courseTitle);
        measures = List.copyOf(measures);
        modules = List.copyOf(modules);
        skillPriorities = List.copyOf(skillPriorities);
        safeguards = List.copyOf(safeguards);
        actionThemes = List.copyOf(actionThemes);
    }

    /** The compact provider view, serialised as JSON data in the user message. */
    public Map<String, Object> prompt() {
        return Map.of("courseTitle", courseTitle, "courseProfile", measures, "analysedModules", modules,
                "skillPriorities", skillPriorities, "safeguards", safeguards, "actionPlanThemes", actionThemes,
                "professionalStandardsRelevant", professionalStandardsRelevant);
    }
}
