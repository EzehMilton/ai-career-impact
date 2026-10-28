package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One deterministic guidance item, with its provenance. The customer-facing report may show only the title and
 * text; the rest records why the item exists.
 *
 * @param moduleIds course positions of the supporting modules, in course order
 * @param evidence the judgment values and flags the rule read, keyed by judgment or flag id
 */
public record UniversityGuidanceItem(
        String guidanceId,
        Section section,
        String ruleId,
        String title,
        String text,
        List<Integer> moduleIds,
        List<UniversityModuleCategory> categories,
        Map<String, Double> evidence) {

    public enum Section {
        AI_LEARNING_OPPORTUNITY,
        OWN_CAPABILITY,
        TECHNICAL_FOUNDATION,
        SAFEGUARD
    }

    public UniversityGuidanceItem {
        Objects.requireNonNull(guidanceId, "guidanceId must not be null");
        Objects.requireNonNull(section, "section must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(text, "text must not be null");
        moduleIds = List.copyOf(Objects.requireNonNull(moduleIds, "moduleIds must not be null"));
        categories = List.copyOf(Objects.requireNonNull(categories, "categories must not be null"));
        evidence = Map.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (moduleIds.isEmpty()) {
            throw new IllegalArgumentException("every guidance item must cite at least one module");
        }
    }
}
