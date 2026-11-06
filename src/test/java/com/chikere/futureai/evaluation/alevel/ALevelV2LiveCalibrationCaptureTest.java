package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The live calibration harness, offline: a stubbed client answers each case with its offline calibration values, so
 * the production adapter's request, response mapping and the capture's output are checked without any Jev call.
 */
class ALevelV2LiveCalibrationCaptureTest {

    @Test
    void eachCaseSendsOnlyItsSubjectsAndTheCaptureLoadsAsACalibrationFile(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        var offline = ALevelV2Calibration.load(ALevelV2Calibration.OFFLINE_JUDGMENTS);
        List<Map<String, ?>> states = new ArrayList<>();
        TypeSafeClient client = mock(TypeSafeClient.class);
        when(client.systemOne(anyMap(), any())).thenAnswer(invocation -> {
            Map<String, ?> state = invocation.getArgument(0);
            states.add(state);
            var calibrationCase = ALevelV2CalibrationCases.ALL.get(states.size() - 1);
            return response(offline.cases().get(calibrationCase.id()), "jev-test-2026-11", "req-" + states.size());
        });

        var capture = new ALevelV2LiveCalibrationCapture();
        var result = capture.capture(client, "jev-latest",
                Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC));

        // One request per case, carrying the subject combination and nothing about the student.
        assertThat(states).hasSize(7);
        for (int index = 0; index < states.size(); index++) {
            assertThat(states.get(index).toString()).contains(ALevelV2CalibrationCases.ALL.get(index).state()
                    .selectedSubjects().getFirst());
            assertThat(states.get(index).keySet()).allSatisfy(key -> assertThat(key)
                    .doesNotContain("grade", "interest", "career", "degree", "country", "context"));
        }
        assertThat(result.cases()).extracting(ALevelV2LiveCalibrationCapture.CaseResult::returnedModel)
                .containsOnly("jev-test-2026-11");

        Path file = dir.resolve("capture.json");
        Files.writeString(file, capture.json(result));
        var loaded = ALevelV2Calibration.load(file);
        assertThat(loaded.source()).isEqualTo("JEV");
        assertThat(loaded.schemaVersion()).isEqualTo("a-level-v2");
        assertThat(loaded.cases().keySet()).containsExactlyElementsOf(
                ALevelV2CalibrationCases.ALL.stream().map(ALevelV2CalibrationCases.CalibrationCase::id).toList());
        assertThat(loaded.cases().get("maths-economics-geography").origin())
                .contains("Live Jev capture 2026-11-06T12:00:00Z", "model jev-test-2026-11", "request req-2");
        // The values round-trip unchanged from what the provider returned.
        loaded.cases().forEach((id, judged) -> judged.judgments().forEach((dimension, judgment) ->
                assertThat(judgment.score()).isEqualTo(offline.cases().get(id).judgments().get(dimension).score())));

        String report = capture.report(result);
        assertThat(report).contains("# A-level v2 live Jev calibration", "a-level-decision-v2 (unchanged)",
                "## maths-economics-geography", "Options Kept Open", "Learning Alongside AI", "Returned model");
        assertThat(capture.json(result)).doesNotContain("TYPESAFE_API_KEY", "apiKey");
    }

    private static SystemOneResponse response(ALevelV2Calibration.CaseJudgments judged, String model,
            String requestId) {
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put(ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT, new NoulAnswer(judged.information()));
        for (String dimension : ALevelDecisionPolicyV2.SCORE_DIMENSIONS) {
            var judgment = judged.judgments().get(dimension);
            int level = (int) Math.round(judgment.score());
            answers.put(dimension, new ScoreAnswer(judgment.score(), Map.of(level, JsonContent.of("level " + level)),
                    Map.of(level, 1.0), judgment.confidence()));
        }
        return new SystemOneResponse(model, answers, Usage.EMPTY, requestId);
    }
}
