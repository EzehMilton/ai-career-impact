package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.judgment.professional.schema.ProfessionalPredicateQuestion;
import com.chikere.futureai.judgment.professional.schema.ProfessionalQuestion;
import com.chikere.futureai.judgment.professional.schema.ProfessionalScoreQuestion;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Jev representation of the canonical provider-independent professional-v1 schema. */
public final class ProfessionalJudgmentSchema {

    public static final String VERSION = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.VERSION;
    public static final String INFORMATION_SUFFICIENT = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT;
    public static final String TASK_REPEATABILITY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.TASK_REPEATABILITY;
    public static final String DIGITAL_EXECUTABILITY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.DIGITAL_EXECUTABILITY;
    public static final String HUMAN_TRUST_DEPENDENCY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.HUMAN_TRUST_DEPENDENCY;
    public static final String PHYSICAL_WORLD_DEPENDENCY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.PHYSICAL_WORLD_DEPENDENCY;
    public static final String CONTEXTUAL_COMPLEXITY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.CONTEXTUAL_COMPLEXITY;
    public static final String ORIGINAL_JUDGMENT = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.ORIGINAL_JUDGMENT;
    public static final String ACCOUNTABILITY_RESPONSIBILITY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.ACCOUNTABILITY_RESPONSIBILITY;
    public static final String AI_COMPLEMENTARITY = com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema.AI_COMPLEMENTARITY;

    private final com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema canonical;
    private final Map<String, Question> questions;

    public ProfessionalJudgmentSchema() {
        this(new com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema());
    }

    public ProfessionalJudgmentSchema(
            com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema canonical) {
        this.canonical = canonical;
        LinkedHashMap<String, Question> adapted = new LinkedHashMap<>();
        canonical.questions().forEach((id, question) -> adapted.put(id, adapt(question)));
        questions = Collections.unmodifiableMap(adapted);
    }

    public String version() { return canonical.version(); }

    public Map<String, Question> questions() { return questions; }

    private static Question adapt(ProfessionalQuestion question) {
        if (question instanceof ProfessionalPredicateQuestion predicate) {
            return Noul.builder().instructions(predicate.instructions())
                    .whenTrue(predicate.whenTrue()).whenFalse(predicate.whenFalse()).build();
        }
        ProfessionalScoreQuestion score = (ProfessionalScoreQuestion) question;
        return Score.of(score.instructions(), score.levels().stream()
                .map(level -> level.description()).toArray(String[]::new));
    }
}
