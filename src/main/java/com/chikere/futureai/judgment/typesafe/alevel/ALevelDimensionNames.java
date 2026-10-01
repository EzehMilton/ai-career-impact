package com.chikere.futureai.judgment.typesafe.alevel;

import java.util.LinkedHashMap;
import java.util.Map;

/** Human-readable names for the a-level-v1 dimensions, defined once. */
public final class ALevelDimensionNames {

    private static final Map<String, String> NAMES;

    static {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        names.put(ALevelJudgmentSchema.INFORMATION_SUFFICIENT, "Information sufficiency");
        names.put(ALevelJudgmentSchema.QUANTITATIVE_REASONING, "Quantitative reasoning");
        names.put(ALevelJudgmentSchema.ANALYTICAL_REASONING, "Analytical reasoning");
        names.put(ALevelJudgmentSchema.WRITTEN_VERBAL_COMMUNICATION, "Written and verbal communication");
        names.put(ALevelJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, "Original judgement and creativity");
        names.put(ALevelJudgmentSchema.CONTEXTUAL_SOCIAL_UNDERSTANDING, "Contextual and social understanding");
        names.put(ALevelJudgmentSchema.TECHNICAL_DIGITAL_FOUNDATION, "Technical and digital foundation");
        names.put(ALevelJudgmentSchema.SUBJECT_SKILL_DIVERSITY, "Subject skill diversity");
        names.put(ALevelJudgmentSchema.DEGREE_PATHWAY_BREADTH, "Degree pathway breadth");
        names.put(ALevelJudgmentSchema.SKILL_TRANSFERABILITY, "Skill transferability");
        names.put(ALevelJudgmentSchema.AI_ERA_COMPLEMENTARITY, "AI-era complementarity");
        NAMES = Map.copyOf(names);
    }

    private ALevelDimensionNames() {
    }

    public static String of(String dimensionId) {
        String name = NAMES.get(dimensionId);
        if (name == null) {
            throw new IllegalArgumentException("Unknown a-level-v1 question: " + dimensionId);
        }
        return name;
    }
}
