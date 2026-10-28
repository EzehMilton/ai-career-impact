package com.chikere.futureai.judgment.typesafe;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shared course- and role-level response check: exactly the questions asked, each validly answered. */
class JevResponseValidationTest {

    private static final Map<String, Question> QUESTIONS = questions();

    @Test
    void acceptsAnExactValidResponseIncludingARoundedDistribution() {
        assertThatNoException().isThrownBy(() -> JevResponseValidation.requireExactAnswers(
                response(noul(0.9), score(3.1, 0.8, Map.of(2, 0.33, 3, 0.33, 4, 0.33))), QUESTIONS));
    }

    @Test
    void rejectsMissingAndUnexpectedAnswers() {
        var missing = new LinkedHashMap<String, Answer>();
        missing.put("information_sufficient", noul(0.9));
        assertRejected(new SystemOneResponse("jev", missing, Usage.EMPTY, null), "missing [analytical_reasoning]");

        var extra = new LinkedHashMap<String, Answer>(answers(noul(0.9), score(2, 0.8, Map.of(2, 1.0))));
        extra.put("degree_pathway_breadth", score(2, 0.8, Map.of(2, 1.0)));
        assertRejected(new SystemOneResponse("jev", extra, Usage.EMPTY, null), "unexpected [degree_pathway_breadth]");
        assertRejected(null, "no answers");
    }

    @Test
    void rejectsTheWrongKindOfAnswer() {
        assertRejected(response(score(2, 0.8, Map.of(2, 1.0)), score(2, 0.8, Map.of(2, 1.0))), "must be a truth value");
        assertRejected(response(noul(0.9), noul(0.4)), "must be a score");
    }

    @Test
    void rejectsOutOfRangeValuesConfidencesAndDistributions() {
        assertRejected(response(noul(1.2), score(2, 0.8, Map.of(2, 1.0))), "value must be between 0 and 1");
        assertRejected(response(noul(0.9), score(4.5, 0.8, Map.of(4, 1.0))), "value must be between 0 and 4");
        assertRejected(response(noul(0.9), score(2, 1.3, Map.of(2, 1.0))), "confidence must be between 0 and 1");
        assertRejected(response(noul(0.9), score(2, 0.8, Map.of(7, 1.0))), "unknown level 7");
        assertRejected(response(noul(0.9), score(2, 0.8, Map.of(2, 0.5, 3, 0.3))), "must sum to 1");
        assertRejected(response(noul(0.9), score(2, 0.8, Map.of(2, -0.1, 3, 1.1))), "probability must be between");
    }

    private static void assertRejected(SystemOneResponse response, String message) {
        assertThatThrownBy(() -> JevResponseValidation.requireExactAnswers(response, QUESTIONS))
                .isInstanceOf(InvalidJevResponseException.class).hasMessageContaining(message);
    }

    private static Map<String, Question> questions() {
        Map<String, Question> questions = new LinkedHashMap<>();
        questions.put("information_sufficient", Noul.builder().instructions("Clear?").whenTrue("Yes").whenFalse("No")
                .build());
        questions.put("analytical_reasoning", Score.of("How much analysis?", "None", "Some", "Half", "Much", "All"));
        return questions;
    }

    private static Map<String, Answer> answers(Answer information, Answer analytical) {
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put("information_sufficient", information);
        answers.put("analytical_reasoning", analytical);
        return answers;
    }

    private static SystemOneResponse response(Answer information, Answer analytical) {
        return new SystemOneResponse("jev", answers(information, analytical), Usage.EMPTY, null);
    }

    private static NoulAnswer noul(double value) {
        return new NoulAnswer(value);
    }

    private static ScoreAnswer score(double value, double confidence, Map<Integer, Double> distribution) {
        return new ScoreAnswer(value, Map.of(2, JsonContent.of("half")), distribution, confidence);
    }
}
