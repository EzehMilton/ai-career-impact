package com.chikere.futureai.decision.openai.poc;

import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

/**
 * Phase 1 translation that copies existing Jev question semantics into Spring AI decision questions.
 * It intentionally depends on both APIs while Jev remains the production provider.
 */
final class JevQuestionDecisionAdapter {

    private JevQuestionDecisionAdapter() {
    }

    static DecisionQuestion adapt(Question question) {
        if (question instanceof Noul noul) {
            return YesNoQuestion.builder()
                    .text(noul.instructions().asText())
                    .whenYes(noul.criteria().whenTrue().asText())
                    .whenNo(noul.criteria().whenFalse().asText())
                    .build();
        }
        if (question instanceof Score score) {
            ScoreQuestion.Builder builder = ScoreQuestion.builder().text(score.instructions().asText());
            for (int level = 0; level <= score.maxLevel(); level++) {
                builder.level(Integer.toString(level), score.levelAt(level).asText());
            }
            return builder.build();
        }
        throw new IllegalArgumentException("Unsupported Jev question type: " + question.getClass().getName());
    }
}
