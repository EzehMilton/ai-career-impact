package com.chikere.futureai.trace;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opt-in diagnostic trace of one assessment, from the submitted form to the report a visitor sees.
 *
 * <p><strong>Sensitive output.</strong> When enabled ({@code app.assessment-trace.enabled}, environment
 * variable {@code ASSESSMENT_TRACE_ENABLED}) the trace deliberately logs user-entered assessment text,
 * the complete prompts sent to the decision and narrative models, and their complete responses. It is
 * for controlled development and diagnosis, not routine production use. It never logs credentials,
 * payment details, cookies, headers or PDF bytes, and every rendered event also passes through
 * {@link TraceRedactor}.</p>
 *
 * <p>When disabled, which is the default, every method returns immediately or runs the supplied work
 * directly: no payload supplier is evaluated, nothing is serialised and nothing is logged.</p>
 *
 * <p>Like {@code ProviderCostTracker}, a trace is held on the current thread. That fits because an
 * assessment's form mapping, decision-model call, policy, persistence, narrative and evidence all run
 * synchronously on the submitting request's thread, so components emit events without any port having
 * to carry a trace identifier. Report assembly happens on a later request and opens its own trace,
 * joined to the submission by the assessment id. Tracing is never load-bearing: a failure to build or
 * write an event is swallowed, and traced work's result or exception passes through unchanged.</p>
 */
public final class AssessmentTrace {

    public static final String PREFIX = "[ASSESSMENT-TRACE]";

    private static final Logger LOGGER = LoggerFactory.getLogger(AssessmentTrace.class);
    private static final int MAXIMUM_ERROR_MESSAGE_LENGTH = 500;
    /** A trace left open by form mapping is only joined by the submission that immediately follows it. */
    private static final Duration MAXIMUM_JOIN_AGE = Duration.ofMinutes(5);
    private static final String REQUEST_ID = "requestId";

    private final boolean enabled;
    /** A label added to every event, such as LIVE_SMOKE; absent for ordinary operation. */
    private final String executionMode;
    /** Receives every event as well as the log, for example the live-smoke trace files; usually absent. */
    private final AssessmentTraceSink sink;
    private final ObjectWriter writer;
    private final ObjectWriter lineWriter;
    private final ThreadLocal<Context> current = new ThreadLocal<>();

    private AssessmentTrace(boolean enabled, String executionMode, AssessmentTraceSink sink) {
        this.enabled = enabled;
        this.executionMode = executionMode;
        this.sink = sink;
        ObjectMapper mapper = enabled
                ? new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                : null;
        this.writer = enabled ? mapper.writerWithDefaultPrettyPrinter() : null;
        this.lineWriter = enabled && sink != null ? mapper.writer() : null;
    }

    /** A trace that records nothing. */
    public static AssessmentTrace disabled() {
        return new AssessmentTrace(false, null, null);
    }

    /** A trace that writes every event to the {@code AssessmentTrace} logger at INFO. */
    public static AssessmentTrace enabled() {
        return new AssessmentTrace(true, null, null);
    }

    /**
     * As {@link #enabled()}, with every event labelled {@code executionMode}, for example LIVE_SMOKE. The label
     * is diagnostic provenance only; nothing reads it to make a decision.
     */
    public static AssessmentTrace enabled(String executionMode) {
        return enabled(executionMode, null);
    }

    /** As {@link #enabled(String)}, also handing every event to {@code sink} when one is supplied. */
    public static AssessmentTrace enabled(String executionMode, AssessmentTraceSink sink) {
        String mode = executionMode == null || executionMode.isBlank() ? null : executionMode.strip();
        return new AssessmentTrace(true, mode, sink);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Emits one event. The payload is only built when tracing is enabled. */
    public void event(JourneyType journey, TraceStage stage, Supplier<?> payload) {
        if (!enabled) {
            return;
        }
        try {
            write(journey, stage, payload.get());
        }
        catch (RuntimeException traceFailure) {
            LOGGER.warn("{} could not record {}: {}", PREFIX, stage, traceFailure.getClass().getSimpleName());
        }
    }

    /**
     * Runs one provider call, measures it, and emits {@code stage} with its outcome, timing and a
     * description of the result. {@code timing}, when not null, is added to the trace summary.
     */
    public <T> T timed(JourneyType journey, TraceStage stage, String timing, Supplier<T> call,
            Function<? super T, ?> describe) {
        if (!enabled) {
            return call.get();
        }
        Instant startedAt = Instant.now();
        long started = System.nanoTime();
        long traceWritingBefore = traceWriting();
        T result;
        try {
            result = call.get();
        }
        catch (RuntimeException failure) {
            long elapsed = elapsedExcludingTrace(started, traceWritingBefore);
            addTiming(timing, elapsed);
            event(journey, stage, () -> outcome(failureStatus(failure), startedAt, elapsed, failure));
            throw failure;
        }
        long elapsed = elapsedExcludingTrace(started, traceWritingBefore);
        addTiming(timing, elapsed);
        event(journey, stage, () -> {
            Map<String, Object> payload = outcome("SUCCEEDED", startedAt, elapsed, null);
            payload.put("result", describe.apply(result));
            return payload;
        });
        return result;
    }

    /** Runs work and adds its duration to the summary under {@code timing}, without emitting an event. */
    public <T> T measured(String timing, Supplier<T> work) {
        if (!enabled) {
            return work.get();
        }
        long started = System.nanoTime();
        long traceWritingBefore = traceWriting();
        try {
            return work.get();
        }
        finally {
            addTiming(timing, elapsedExcludingTrace(started, traceWritingBefore));
        }
    }

    /**
     * Opens the trace for a submission at the form, logs what was submitted and what it became after
     * trimming and mapping, and leaves the trace open for {@link #assessment} to join.
     */
    public <S> S formMapping(JourneyType journey, Supplier<?> form, Supplier<S> mapping,
            Function<? super S, ?> domainState) {
        if (!enabled) {
            return mapping.get();
        }
        Context context = open(journey);
        event(journey, TraceStage.START, () -> start(context, "FORM_SUBMISSION"));
        event(journey, TraceStage.FORM_INPUT, form);
        long started = System.nanoTime();
        S state;
        try {
            state = mapping.get();
        }
        catch (RuntimeException failure) {
            event(journey, TraceStage.FORM_MAPPING_FAILED, () -> Map.of(
                    "errorType", failure.getClass().getName(), "errorMessage", safeMessage(failure)));
            current.remove();
            throw failure;
        }
        long elapsed = System.nanoTime() - started;
        addTiming("formToDomainMs", elapsed);
        event(journey, TraceStage.DOMAIN_INPUT, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("formToDomainMs", millis(elapsed));
            payload.put("state", domainState.apply(state));
            return payload;
        });
        return state;
    }

    /**
     * Runs a whole submission. Joins the trace that form mapping opened on this request, or opens one,
     * and always closes it with a {@link TraceStage#SUMMARY} of every measured timing.
     */
    public UUID assessment(JourneyType journey, Supplier<UUID> submission) {
        if (!enabled) {
            return submission.get();
        }
        Context context = join(journey);
        try {
            UUID id = submission.get();
            context.assessmentId = id;
            event(journey, TraceStage.SUMMARY, () -> summary(context, "COMPLETED", null));
            return id;
        }
        catch (RuntimeException failure) {
            event(journey, TraceStage.SUMMARY, () -> summary(context, "FAILED", failure));
            throw failure;
        }
        finally {
            current.remove();
        }
    }

    /** Associates the persisted assessment id with the open trace, once it exists. */
    public void assessmentId(UUID assessmentId) {
        if (!enabled) {
            return;
        }
        Context context = current.get();
        if (context != null) {
            context.assessmentId = assessmentId;
        }
    }

    /**
     * Runs work for one existing assessment under its own trace of the given kind, so its events carry the
     * assessment id, then restores whatever trace was open before. Emits nothing itself.
     */
    public <T> T scoped(JourneyType journey, UUID assessmentId, String kind, Supplier<T> work) {
        if (!enabled) {
            return work.get();
        }
        Context previous = current.get();
        Context context = new Context(journey, kind);
        context.assessmentId = assessmentId;
        current.set(context);
        try {
            return work.get();
        }
        finally {
            if (previous == null) {
                current.remove();
            }
            else {
                current.set(previous);
            }
        }
    }

    /**
     * Assembles a free result or a detailed report under its own trace, logging the structured report
     * that feeds the HTML and PDF views, then a timing summary.
     */
    public <R> Optional<R> report(JourneyType journey, UUID assessmentId, boolean detailed,
            Supplier<Optional<R>> assembly) {
        if (!enabled) {
            return assembly.get();
        }
        Context previous = current.get();
        Context context = new Context(journey, "REPORT_ASSEMBLY");
        context.assessmentId = assessmentId;
        current.set(context);
        TraceStage stage = detailed ? TraceStage.DETAILED_REPORT_COMPLETE : TraceStage.FREE_ASSESSMENT_COMPLETE;
        try {
            Optional<R> report = measured("reportAssemblyMs", assembly);
            event(journey, stage, () -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("tier", detailed ? "DETAILED" : "FREE");
                payload.put("found", report.isPresent());
                payload.put("report", report.orElse(null));
                return payload;
            });
            event(journey, TraceStage.REPORT_SUMMARY, () -> summary(context, "COMPLETED", null));
            return report;
        }
        catch (RuntimeException failure) {
            event(journey, TraceStage.REPORT_SUMMARY, () -> summary(context, "FAILED", failure));
            throw failure;
        }
        finally {
            if (previous == null) {
                current.remove();
            }
            else {
                current.set(previous);
            }
        }
    }

    /** The open trace's identifier on this thread, if tracing is enabled and a trace is open. */
    public Optional<String> currentTraceId() {
        Context context = enabled ? current.get() : null;
        return context == null ? Optional.empty() : Optional.of(context.traceId);
    }

    /** The journey of the trace open on this thread, for components that do not know it themselves. */
    JourneyType currentJourney() {
        Context context = enabled ? current.get() : null;
        return context == null ? null : context.journey;
    }

    /** A provider exception's type and message, truncated and redacted, for a FAILED event. */
    public static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null) {
            return "";
        }
        String truncated = message.length() > MAXIMUM_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAXIMUM_ERROR_MESSAGE_LENGTH) + "..." : message;
        return TraceRedactor.redact(truncated);
    }

    private Context open(JourneyType journey) {
        Context context = new Context(journey, "ASSESSMENT_SUBMISSION");
        current.set(context);
        return context;
    }

    private Context join(JourneyType journey) {
        Context existing = current.get();
        if (existing != null && existing.journey == journey && existing.kind.equals("ASSESSMENT_SUBMISSION")
                && Objects.equals(existing.requestId, MDC.get(REQUEST_ID))
                && Duration.between(existing.startedAt, Instant.now()).compareTo(MAXIMUM_JOIN_AGE) < 0) {
            return existing;
        }
        Context context = open(journey);
        event(journey, TraceStage.START, () -> start(context, "SERVICE_CALL"));
        return context;
    }

    private long traceWriting() {
        Context context = current.get();
        return context == null ? 0L : context.traceWritingNanos;
    }

    /**
     * Elapsed time minus the time this trace spent writing events in the meantime, so a stage that
     * emits events internally, such as the decision policy, is not reported as slower than it is.
     */
    private long elapsedExcludingTrace(long started, long traceWritingBefore) {
        return Math.max(0L, System.nanoTime() - started - (traceWriting() - traceWritingBefore));
    }

    private void addTiming(String timing, long nanos) {
        Context context = current.get();
        if (timing != null && context != null) {
            context.timings.merge(timing, nanos, Long::sum);
        }
    }

    private void write(JourneyType journey, TraceStage stage, Object payload) {
        long started = System.nanoTime();
        Context context = current.get();
        try {
            writeEvent(context, journey, stage, payload);
        }
        finally {
            if (context != null) {
                context.traceWritingNanos += System.nanoTime() - started;
            }
        }
    }

    private void writeEvent(Context context, JourneyType journey, TraceStage stage, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("stage", stage);
        envelope.put("journey", journey);
        if (executionMode != null) {
            envelope.put("executionMode", executionMode);
        }
        envelope.put("assessmentTraceId", context == null ? null : context.traceId);
        envelope.put("assessmentId", context == null ? null : context.assessmentId);
        envelope.put("sequence", context == null ? null : ++context.sequence);
        envelope.put("at", Instant.now());
        envelope.put("payload", payload);
        String json;
        try {
            json = TraceRedactor.redact(writer.writeValueAsString(envelope));
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException serialisationFailure) {
            throw new IllegalStateException("trace payload could not be serialised", serialisationFailure);
        }
        String traceId = context == null ? "none" : context.traceId;
        String assessmentId = context == null || context.assessmentId == null
                ? "pending" : context.assessmentId.toString();
        LOGGER.atInfo()
                .addKeyValue("assessmentTraceId", traceId)
                .addKeyValue("assessmentId", assessmentId)
                .addKeyValue("journey", journey)
                .addKeyValue("traceStage", stage)
                .log("{} {} journey={} assessmentTraceId={} assessmentId={}\n{}",
                        PREFIX, stage, journey, traceId, assessmentId, json);
        if (sink != null) {
            try {
                sink.accept(context == null ? null : context.traceId, context == null ? null : context.assessmentId,
                        TraceRedactor.redact(lineWriter.writeValueAsString(envelope)));
            }
            catch (com.fasterxml.jackson.core.JsonProcessingException serialisationFailure) {
                throw new IllegalStateException("trace payload could not be serialised", serialisationFailure);
            }
        }
    }

    private static Map<String, Object> start(Context context, String origin) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origin", origin);
        payload.put("kind", context.kind);
        payload.put("requestId", context.requestId);
        payload.put("startedAt", context.startedAt);
        return payload;
    }

    private static Map<String, Object> summary(Context context, String status, RuntimeException failure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", context.kind);
        payload.put("status", status);
        if (failure != null) {
            payload.put("errorType", failure.getClass().getName());
            payload.put("errorMessage", safeMessage(failure));
        }
        Map<String, Object> timings = new LinkedHashMap<>();
        context.timings.forEach((name, nanos) -> timings.put(name, millis(nanos)));
        timings.put(context.kind.equals("REPORT_ASSEMBLY") ? "totalReportMs" : "totalAssessmentMs",
                millis(System.nanoTime() - context.startedNanos));
        timings.put("traceWritingMs", millis(context.traceWritingNanos));
        payload.put("timings", timings);
        payload.put("note", "Only genuinely measured timings are listed; a stage that did not run is absent. "
                + "Stage timings exclude the trace's own writing time, which the total includes.");
        return payload;
    }

    private static Map<String, Object> outcome(String status, Instant startedAt, long elapsedNanos,
            RuntimeException failure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("startedAt", startedAt);
        payload.put("completedAt", startedAt.plusNanos(elapsedNanos));
        payload.put("durationMs", millis(elapsedNanos));
        if (failure != null) {
            payload.put("errorType", failure.getClass().getName());
            payload.put("errorMessage", safeMessage(failure));
        }
        return payload;
    }

    private static String failureStatus(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) {
                return "TIMED_OUT";
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return "FAILED";
    }

    private static double millis(long nanos) {
        return Math.round(nanos / 10_000.0) / 100.0;
    }

    private static final class Context {
        private final String traceId = UUID.randomUUID().toString();
        private final JourneyType journey;
        private final String kind;
        private final String requestId = MDC.get(REQUEST_ID);
        private final Instant startedAt = Instant.now();
        private final long startedNanos = System.nanoTime();
        private final Map<String, Long> timings = new LinkedHashMap<>();
        private UUID assessmentId;
        private long sequence;
        private long traceWritingNanos;

        private Context(JourneyType journey, String kind) {
            this.journey = journey;
            this.kind = kind;
        }
    }
}
