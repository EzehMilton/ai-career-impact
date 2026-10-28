package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.judgment.typesafe.JevTraceRendering;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.api.TypeSafeApi;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The real TypeSafe client, remembering the last raw response the production University adapter received. The
 * capture survives an adapter mapping failure, so one live call is enough to see what Jev actually returned.
 */
final class RecordingTypeSafeClient extends TypeSafeClient
        implements UniversityBaselineHarness.RawResponses, UniversityModuleProbe.RawResponses {

    private SystemOneResponse last;

    private RecordingTypeSafeClient(TypeSafeApi api, TypeSafeClient defaults) {
        super(api, defaults.defaultModel(), defaults.retryPolicy(), defaults.timeout());
    }

    static RecordingTypeSafeClient create(String apiKey, String model) {
        TypeSafeClient defaults = TypeSafeClient.builder().apiKey(apiKey).defaultModel(model).build();
        return new RecordingTypeSafeClient(TypeSafeApi.builder().apiKey(apiKey).build(), defaults);
    }

    @Override
    public SystemOneResponse systemOne(Map<String, ?> state, Map<String, ? extends Question> questions) {
        last = null;
        SystemOneResponse response = super.systemOne(state, questions);
        last = response;
        return response;
    }

    /** The last raw response itself, taken once; kept even when the adapter rejected it. */
    @Override
    public Optional<SystemOneResponse> takeResponse() {
        SystemOneResponse response = last;
        last = null;
        return Optional.ofNullable(response);
    }

    @Override
    public Optional<Map<String, Object>> take() {
        return takeResponse().map(RecordingTypeSafeClient::render);
    }

    /** Jev's raw answers as rendered for traces, plus each score answer's probability sum. */
    static Map<String, Object> render(SystemOneResponse response) {
        Map<String, Object> raw = new LinkedHashMap<>(JevTraceRendering.response(response));
        // The probability sum of each score answer, exactly as returned, for the distribution contract check.
        Map<String, Object> sums = new LinkedHashMap<>();
        if (response.answers() != null) {
            response.answers().forEach((id, answer) -> {
                if (answer instanceof ScoreAnswer score && score.probabilities() != null) {
                    sums.put(id, score.probabilities().values().stream().mapToDouble(Double::doubleValue).sum());
                }
            });
        }
        raw.put("probabilitySums", sums);
        return raw;
    }
}
