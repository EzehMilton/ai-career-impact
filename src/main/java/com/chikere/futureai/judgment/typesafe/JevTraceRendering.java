package com.chikere.futureai.judgment.typesafe;

import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the exact Jev System One request and raw response for the assessment trace.
 *
 * <p>A System One call has no free-text prompt of its own: the request is the state map plus the
 * schema's questions, each with its instructions and level criteria. Both are rendered in full. The
 * client's credentials are never read.</p>
 */
public final class JevTraceRendering {

    private JevTraceRendering() {
    }

    public static Map<String, Object> request(Map<String, Object> state, Map<String, ? extends Question> questions,
            String schemaVersion, String configuredModel, TypeSafeClient client) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "JEV");
        payload.put("call", "TypeSafeClient.systemOne(state, questions)");
        payload.put("schemaVersion", schemaVersion);
        payload.put("configuredModel", configuredModel);
        payload.put("clientDefaultModel", client.defaultModel());
        payload.put("timeout", client.timeout());
        RetryPolicy retry = client.retryPolicy();
        if (retry != null) {
            Map<String, Object> retryPolicy = new LinkedHashMap<>();
            retryPolicy.put("maxRetries", retry.maxRetries());
            retryPolicy.put("initialBackoff", retry.initialBackoff());
            retryPolicy.put("maxBackoff", retry.maxBackoff());
            retryPolicy.put("retryableStatuses", retry.retryableStatuses());
            payload.put("retryPolicy", retryPolicy);
        }
        payload.put("systemInstructions", "none: System One takes structured questions, not a free-text system prompt");
        payload.put("state", state);
        Map<String, Object> rendered = new LinkedHashMap<>();
        questions.forEach((id, question) -> rendered.put(id, question(question)));
        payload.put("questions", rendered);
        return payload;
    }

    public static Map<String, Object> response(SystemOneResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (response == null) {
            payload.put("response", null);
            return payload;
        }
        payload.put("model", response.model());
        payload.put("requestId", response.requestId());
        if (response.usage() != null) {
            payload.put("inputTokens", response.usage().inputTokens());
            payload.put("outputTokens", response.usage().outputTokens());
        }
        Map<String, Object> answers = new LinkedHashMap<>();
        if (response.answers() != null) {
            response.answers().forEach((id, answer) -> answers.put(id, answer(answer)));
        }
        payload.put("answers", answers);
        payload.put("note", "Raw answers as Jev returned them, before the application maps them to judgments.");
        return payload;
    }

    private static Map<String, Object> question(Question question) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("type", question.typeName());
        rendered.put("instructions", text(question.instructions()));
        if (question instanceof Score score) {
            Map<Integer, String> levels = new LinkedHashMap<>();
            for (int level = 0; level < score.criteria().size(); level++) {
                levels.put(level, text(score.criteria().get(level)));
            }
            rendered.put("levels", levels);
        }
        else if (question instanceof Noul noul && noul.criteria() != null) {
            rendered.put("whenTrue", text(noul.criteria().whenTrue()));
            rendered.put("whenFalse", text(noul.criteria().whenFalse()));
        }
        return rendered;
    }

    private static Map<String, Object> answer(Answer answer) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        if (answer instanceof ScoreAnswer score) {
            rendered.put("type", "SCORE");
            rendered.put("value", score.value());
            rendered.put("nearestLevel", score.nearestLevel());
            rendered.put("nearestLabel", score.nearestLabel());
            rendered.put("confidence", score.confidence());
            rendered.put("probabilities", score.probabilities());
        }
        else if (answer instanceof NoulAnswer noul) {
            rendered.put("type", "NOUL");
            rendered.put("value", noul.value());
        }
        else {
            rendered.put("type", answer == null ? null : answer.getClass().getSimpleName());
            rendered.put("value", String.valueOf(answer));
        }
        return rendered;
    }

    private static String text(JsonContent content) {
        return content == null || content.isNull() ? null : content.toDisplayString();
    }
}
