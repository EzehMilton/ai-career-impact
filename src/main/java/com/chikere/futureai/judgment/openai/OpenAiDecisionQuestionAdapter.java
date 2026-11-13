package com.chikere.futureai.judgment.openai;

import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreLevel;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import java.util.stream.IntStream;

/** Preserves the existing question text and ordered criteria while adapting it to OpenAI Decisions. */
public final class OpenAiDecisionQuestionAdapter {

    private OpenAiDecisionQuestionAdapter() {}

    public static DecisionQuestion adapt(Question question) {
        if (question instanceof Noul noul) {
            return YesNoQuestion.builder().text(noul.instructions().asText())
                    .whenYes(noul.criteria().whenTrue().asText())
                    .whenNo(noul.criteria().whenFalse().asText()).build();
        }
        if (question instanceof Score score) {
            return ScoreQuestion.builder().text(score.instructions().asText())
                    .levels(IntStream.range(0, score.criteria().size()).mapToObj(index -> ScoreLevel.builder()
                            .label(Integer.toString(index))
                            .description(score.criteria().get(index).asText()).build()).toList())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported judgment question type: " + question.getClass().getName());
    }
}
