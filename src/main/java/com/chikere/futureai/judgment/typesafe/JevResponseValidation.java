package com.chikere.futureai.judgment.typesafe;

import com.chikere.futureai.judgment.domain.ProbabilityDistributions;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shared strictness check for a course- or role-level System One response, applied before any answer is mapped:
 * exactly the questions asked must be answered, each with the right kind of answer and an in-range value, confidence
 * and probability distribution. A response that fails is rejected as a whole; nothing is filled in, dropped or
 * clamped, so a malformed provider answer can never reach a decision policy.
 *
 * <p>The University module and Professional responsibility adapters apply the same rules through their own typed
 * judgments. A JSON object with a repeated answer name is collapsed by the SDK's parser before it reaches the
 * application, so that case cannot be distinguished here.</p>
 */
public final class JevResponseValidation {

    private JevResponseValidation() {
    }

    /** @throws InvalidJevResponseException when the response does not answer exactly {@code questions} validly */
    public static void requireExactAnswers(SystemOneResponse response, Map<String, ? extends Question> questions) {
        if (response == null || response.answers() == null) {
            throw new InvalidJevResponseException("the provider returned no answers");
        }
        Map<String, Answer> answers = response.answers();
        Set<String> missing = new TreeSet<>(questions.keySet());
        missing.removeAll(answers.keySet());
        Set<String> unexpected = new TreeSet<>(answers.keySet());
        unexpected.removeAll(questions.keySet());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new InvalidJevResponseException("the provider answers did not match the questions asked: missing "
                    + missing + ", unexpected " + unexpected);
        }
        questions.forEach((name, question) -> requireValid(name, question, answers.get(name)));
    }

    private static void requireValid(String name, Question question, Answer answer) {
        if (question instanceof Noul) {
            if (!(answer instanceof NoulAnswer noul)) {
                throw wrongType(name, "a truth value", answer);
            }
            requireUnit(noul.value(), "answer " + name + " value");
        }
        else if (question instanceof Score score) {
            if (!(answer instanceof ScoreAnswer scored)) {
                throw wrongType(name, "a score", answer);
            }
            int maximumLevel = score.criteria().size() - 1;
            double value = scored.value();
            if (!Double.isFinite(value) || value < 0 || value > maximumLevel) {
                throw new InvalidJevResponseException("answer " + name + " value must be between 0 and "
                        + maximumLevel + ", was " + value);
            }
            requireUnit(scored.confidence(), "answer " + name + " confidence");
            Map<Integer, Double> distribution = scored.probabilities();
            if (distribution != null && !distribution.isEmpty()) {
                double total = 0;
                for (var level : distribution.entrySet()) {
                    if (level.getKey() == null || level.getKey() < 0 || level.getKey() > maximumLevel) {
                        throw new InvalidJevResponseException("answer " + name + " has a probability for an unknown "
                                + "level " + level.getKey());
                    }
                    requireUnit(level.getValue() == null ? Double.NaN : level.getValue(),
                            "answer " + name + " probability");
                    total += level.getValue();
                }
                if (!ProbabilityDistributions.sumsToOne(total)) {
                    throw new InvalidJevResponseException("answer " + name + " probabilities must sum to 1, were "
                            + total);
                }
            }
        }
        else {
            throw new InvalidJevResponseException("question " + name + " is of an unsupported kind");
        }
    }

    private static void requireUnit(double value, String description) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new InvalidJevResponseException(description + " must be between 0 and 1, was " + value);
        }
    }

    private static InvalidJevResponseException wrongType(String name, String expected, Answer answer) {
        return new InvalidJevResponseException("answer " + name + " must be " + expected + ", was "
                + (answer == null ? "absent" : answer.getClass().getSimpleName()));
    }
}
