package com.chikere.futureai.trace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Collects what the assessment trace writes, as rendered lines and as parsed JSON events. */
public final class TraceLogCapture implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Logger logger = (Logger) LoggerFactory.getLogger(AssessmentTrace.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private TraceLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    public static TraceLogCapture start() {
        return new TraceLogCapture();
    }

    /** Every trace line exactly as it would be logged. */
    public List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Every trace line's JSON body: the envelope with stage, journey, ids and payload. */
    public List<JsonNode> events() {
        return lines().stream()
                .filter(line -> line.startsWith(AssessmentTrace.PREFIX))
                .map(line -> {
                    try {
                        return JSON.readTree(line.substring(line.indexOf('\n') + 1));
                    }
                    catch (Exception exception) {
                        throw new IllegalStateException("trace line is not followed by JSON: " + line, exception);
                    }
                })
                .toList();
    }

    public List<String> stages() {
        return events().stream().map(event -> event.get("stage").asText()).toList();
    }

    public List<JsonNode> events(TraceStage stage) {
        return events().stream().filter(event -> event.get("stage").asText().equals(stage.name())).toList();
    }

    public JsonNode only(TraceStage stage) {
        List<JsonNode> matching = events(stage);
        if (matching.size() != 1) {
            throw new AssertionError("expected one " + stage + " event but found " + matching.size());
        }
        return matching.getFirst();
    }

    public String all() {
        return String.join("\n", lines());
    }

    public void clear() {
        appender.list.clear();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
