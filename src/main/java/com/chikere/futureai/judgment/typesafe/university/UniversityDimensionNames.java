package com.chikere.futureai.judgment.typesafe.university;

import java.util.LinkedHashMap;
import java.util.Map;

/** Human-readable names for the university-v1 dimensions, defined once. */
public final class UniversityDimensionNames {

    private static final Map<String, String> NAMES;

    static {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        names.put(UniversityJudgmentSchema.INFORMATION_SUFFICIENT, "Information sufficiency");
        names.put(UniversityJudgmentSchema.TECHNICAL_DIGITAL_DEPTH, "Technical and digital depth");
        names.put(UniversityJudgmentSchema.ANALYTICAL_REASONING, "Analytical reasoning");
        names.put(UniversityJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, "Original judgment and creativity");
        names.put(UniversityJudgmentSchema.HUMAN_RELATIONSHIP_DEPENDENCY, "Human and relationship capability");
        names.put(UniversityJudgmentSchema.CONTEXTUAL_COMPLEXITY, "Contextual complexity");
        names.put(UniversityJudgmentSchema.PRACTICAL_REAL_WORLD_APPLICATION, "Practical real-world application");
        names.put(UniversityJudgmentSchema.AI_COMPLEMENTARITY, "AI complementarity");
        names.put(UniversityJudgmentSchema.SKILL_TRANSFERABILITY, "Skill transferability");
        names.put(UniversityJudgmentSchema.CAREER_BREADTH, "Career breadth");
        NAMES = Map.copyOf(names);
    }

    private UniversityDimensionNames() {
    }

    public static String of(String dimensionId) {
        String name = NAMES.get(dimensionId);
        if (name == null) {
            throw new IllegalArgumentException("Unknown university-v1 question: " + dimensionId);
        }
        return name;
    }
}
