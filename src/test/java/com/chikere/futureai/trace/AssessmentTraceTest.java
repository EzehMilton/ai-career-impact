package com.chikere.futureai.trace;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentTraceTest {

    private final TraceLogCapture log = TraceLogCapture.start();

    @AfterEach
    void detach() {
        log.close();
    }

    @Test
    void tracingIsDisabledUnlessThePropertyEnablesIt() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(AssessmentTraceConfiguration.class);

        runner.run(context -> {
            assertThat(context.getBean(AssessmentTrace.class).isEnabled()).isFalse();
            assertThat(context).doesNotHaveBean(ChatClientCustomizer.class);
        });
        runner.withPropertyValues("app.assessment-trace.enabled=false").run(context ->
                assertThat(context.getBean(AssessmentTrace.class).isEnabled()).isFalse());
        runner.withPropertyValues("app.assessment-trace.enabled=true").run(context -> {
            assertThat(context.getBean(AssessmentTrace.class).isEnabled()).isTrue();
            assertThat(context).hasSingleBean(ChatClientCustomizer.class);
        });
    }

    @Test
    void theShippedDefaultReadsTheEnvironmentVariableAndFallsBackToFalse() throws Exception {
        // Read from the source tree: test resources carry their own application.properties.
        var properties = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        assertThat(properties.getProperty("app.assessment-trace.enabled"))
                .isEqualTo("${ASSESSMENT_TRACE_ENABLED:false}");
    }

    @Test
    void aDisabledTraceEmitsNothingAndNeverBuildsAPayload() {
        AssessmentTrace trace = AssessmentTrace.disabled();
        AtomicBoolean built = new AtomicBoolean();

        trace.event(JourneyType.PROFESSIONAL, TraceStage.START, () -> {
            built.set(true);
            return Map.of();
        });
        String mapped = trace.formMapping(JourneyType.PROFESSIONAL, () -> {
            built.set(true);
            return "form";
        }, () -> "state", state -> {
            built.set(true);
            return state;
        });
        Integer called = trace.timed(JourneyType.PROFESSIONAL, TraceStage.DECISION_MODEL_RESPONSE, "decisionModelMs",
                () -> 42, result -> {
                    built.set(true);
                    return result;
                });
        UUID id = UUID.randomUUID();

        assertThat(mapped).isEqualTo("state");
        assertThat(called).isEqualTo(42);
        assertThat(trace.assessment(JourneyType.PROFESSIONAL, () -> id)).isEqualTo(id);
        assertThat(built).isFalse();
        assertThat(log.lines()).isEmpty();
        assertThat(trace.currentTraceId()).isEmpty();
    }

    @Test
    void anEnabledTraceWritesAPrefixedStageAndCorrelatesOneSubmissionUnderOneTraceId() {
        AssessmentTrace trace = AssessmentTrace.enabled();
        UUID id = UUID.randomUUID();

        trace.formMapping(JourneyType.UNIVERSITY, () -> Map.of("courseTitle", "  Law "), () -> "Law", state -> state);
        trace.assessment(JourneyType.UNIVERSITY, () -> {
            trace.assessmentId(id);
            trace.event(JourneyType.UNIVERSITY, TraceStage.PERSISTENCE, () -> Map.of("saved", true));
            return id;
        });

        assertThat(log.lines()).allSatisfy(line -> assertThat(line).startsWith("[ASSESSMENT-TRACE] "));
        assertThat(log.lines().getFirst()).startsWith("[ASSESSMENT-TRACE] START journey=UNIVERSITY assessmentTraceId=");
        assertThat(log.stages()).containsExactly("START", "FORM_INPUT", "DOMAIN_INPUT", "PERSISTENCE", "SUMMARY");
        assertThat(log.events()).extracting(event -> event.get("assessmentTraceId").asText()).containsOnly(
                log.events().getFirst().get("assessmentTraceId").asText());
        assertThat(log.events()).extracting(event -> event.get("sequence").asInt()).containsExactly(1, 2, 3, 4, 5);
        assertThat(log.only(TraceStage.FORM_INPUT).at("/payload/courseTitle").asText()).isEqualTo("  Law ");
        assertThat(log.only(TraceStage.DOMAIN_INPUT).at("/payload/state").asText()).isEqualTo("Law");
        assertThat(log.only(TraceStage.PERSISTENCE).get("assessmentId").asText()).isEqualTo(id.toString());
        JsonNode summary = log.only(TraceStage.SUMMARY);
        assertThat(summary.at("/payload/status").asText()).isEqualTo("COMPLETED");
        assertThat(summary.at("/payload/timings").has("formToDomainMs")).isTrue();
        assertThat(summary.at("/payload/timings").has("totalAssessmentMs")).isTrue();
        // The trace is closed afterwards, so the next request on a pooled thread starts cleanly.
        assertThat(trace.currentTraceId()).isEmpty();
    }

    @Test
    void aTimedCallReportsItsMeasuredDurationAndOutcomeAndRethrowsTheSameFailure() {
        AssessmentTrace trace = AssessmentTrace.enabled();
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        RuntimeException timeout = new RuntimeException("gave up", new TimeoutException("read timed out"));

        trace.assessment(JourneyType.A_LEVEL, () -> {
            trace.timed(JourneyType.A_LEVEL, TraceStage.DECISION_MODEL_RESPONSE, "decisionModelMs", () -> {
                sleep(20);
                return "judgments";
            }, value -> value);
            assertThatThrownBy(() -> trace.timed(JourneyType.A_LEVEL, TraceStage.NARRATIVE_RESPONSE,
                    "narrativeModelMs", () -> { throw failure; }, value -> value)).isSameAs(failure);
            assertThatThrownBy(() -> trace.timed(JourneyType.A_LEVEL, TraceStage.EVIDENCE_RESPONSE,
                    "evidenceMs", () -> { throw timeout; }, value -> value)).isSameAs(timeout);
            return UUID.randomUUID();
        });

        JsonNode succeeded = log.only(TraceStage.DECISION_MODEL_RESPONSE).get("payload");
        assertThat(succeeded.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.get("durationMs").asDouble()).isGreaterThanOrEqualTo(20.0);
        assertThat(succeeded.get("startedAt").asText()).isNotBlank();
        assertThat(succeeded.get("completedAt").asText()).isNotBlank();
        assertThat(succeeded.get("result").asText()).isEqualTo("judgments");
        JsonNode failed = log.only(TraceStage.NARRATIVE_RESPONSE).get("payload");
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("errorType").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failed.get("errorMessage").asText()).isEqualTo("provider unavailable");
        assertThat(log.only(TraceStage.EVIDENCE_RESPONSE).at("/payload/status").asText()).isEqualTo("TIMED_OUT");
        assertThat(log.only(TraceStage.SUMMARY).at("/payload/timings/decisionModelMs").asDouble())
                .isGreaterThanOrEqualTo(20.0);
    }

    @Test
    void credentialShapedTextIsRedactedFromEveryEvent() {
        AssessmentTrace trace = AssessmentTrace.enabled();

        trace.event(JourneyType.PROFESSIONAL, TraceStage.START, () -> Map.of(
                "openai", "sk-proj-abcdefghijklmnop1234",
                "stripe", "sk_test_51Habcdefghijklmnop",
                "webhook", "whsec_abcdefghijklmnop",
                "header", "Authorization: Bearer abcdefghijklmnopqrstuvwxyz",
                "config", "api-key=supersecretvalue password: hunter2hunter2"));

        String output = log.all();
        assertThat(output).doesNotContain("sk-proj-abcdefghijklmnop1234", "sk_test_51Habcdefghijklmnop",
                "whsec_abcdefghijklmnop", "abcdefghijklmnopqrstuvwxyz", "supersecretvalue", "hunter2hunter2");
        assertThat(output).contains("[REDACTED]");
    }

    @Test
    void aFailingMappingClosesTheTraceAndPassesTheFailureThrough() {
        AssessmentTrace trace = AssessmentTrace.enabled();
        IllegalArgumentException invalid = new IllegalArgumentException("unknown subject");

        assertThatThrownBy(() -> trace.formMapping(JourneyType.A_LEVEL, () -> Map.of(), () -> { throw invalid; },
                state -> state)).isSameAs(invalid);

        assertThat(log.stages()).containsExactly("START", "FORM_INPUT", "FORM_MAPPING_FAILED");
        assertThat(trace.currentTraceId()).isEmpty();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
