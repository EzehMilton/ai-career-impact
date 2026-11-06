package com.chikere.futureai.evaluation.alevel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The A-level v2 release-gate calibration: seven real Jev calls, one per {@link ALevelV2CalibrationCases} combination,
 * through the production adapter. Never selected by Surefire or ordinary verify; run only on the full-provider machine:
 *
 * <pre>TYPESAFE_API_KEY=... ./mvnw -Plive-a-level-calibration verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false</pre>
 *
 * <p>It writes {@code target/a-level-calibration/live/a-level-v2-live-calibration.json} (the calibration loader's
 * format, source {@code JEV}) and a review report beside it. Promoting the file to the offline calibration fixture is
 * a separate, reviewed step: nothing here changes a fixture, and ordinary tests never call Jev.</p>
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "a-level.live.calibration.enabled", matches = "true")
class ALevelV2LiveCalibrationIT {

    static final Path OUTPUT = Path.of("target", "a-level-calibration", "live");

    @Test
    void capturesTheSevenCalibrationCombinationsFromJev() throws Exception {
        String model = System.getProperty("spring.ai.typesafe.model", "jev-latest");
        // The production client settings: the whole exchange timed at 60 seconds and no automatic retry, so each
        // case is exactly one charged System One request.
        TypeSafeClient client = TypeSafeClient.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .defaultModel(model)
                .timeout(Duration.ofSeconds(60))
                .retryPolicy(RetryPolicy.noRetry())
                .build();

        var capture = new ALevelV2LiveCalibrationCapture();
        var result = capture.capture(client, model, Clock.systemUTC());

        Files.createDirectories(OUTPUT);
        Path json = OUTPUT.resolve("a-level-v2-live-calibration.json");
        Path report = OUTPUT.resolve("a-level-v2-live-calibration.md");
        Files.writeString(json, capture.json(result), StandardCharsets.UTF_8);
        Files.writeString(report, capture.report(result), StandardCharsets.UTF_8);
        System.out.println("A-level v2 live calibration written to " + json.toAbsolutePath() + " and "
                + report.toAbsolutePath());

        // The capture loads exactly as the offline calibration does, so promoting it needs no code change.
        var loaded = ALevelV2Calibration.load(json);
        assertThat(loaded.source()).isEqualTo("JEV");
        assertThat(loaded.cases()).hasSize(ALevelV2CalibrationCases.ALL.size());
    }
}
