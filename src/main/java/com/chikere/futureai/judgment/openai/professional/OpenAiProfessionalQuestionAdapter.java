package com.chikere.futureai.judgment.openai.professional;

import com.chikere.futureai.judgment.professional.schema.ProfessionalPredicateQuestion;
import com.chikere.futureai.judgment.professional.schema.ProfessionalQuestion;
import com.chikere.futureai.judgment.professional.schema.ProfessionalScoreQuestion;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreLevel;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

final class OpenAiProfessionalQuestionAdapter {

    private OpenAiProfessionalQuestionAdapter() {}

    static DecisionQuestion adapt(ProfessionalQuestion question) {
        if (question instanceof ProfessionalPredicateQuestion predicate) {
            return YesNoQuestion.builder().text(predicate.instructions())
                    .whenYes(predicate.whenTrue()).whenNo(predicate.whenFalse()).build();
        }
        ProfessionalScoreQuestion score = (ProfessionalScoreQuestion) question;
        return ScoreQuestion.builder().text(score.instructions())
                .levels(score.levels().stream().map(level -> ScoreLevel.builder()
                        .label(Integer.toString(level.value()))
                        .description(level.description()).build()).toList())
                .build();
    }
}
