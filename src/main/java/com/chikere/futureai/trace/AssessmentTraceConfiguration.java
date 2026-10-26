package com.chikere.futureai.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the opt-in assessment trace. Controlled only by {@code app.assessment-trace.enabled}
 * ({@code ASSESSMENT_TRACE_ENABLED}) and off unless enabled; only the local live-smoke profile changes that
 * flag's default to true. {@code app.assessment-trace.execution-mode} optionally labels every event.
 */
@Configuration(proxyBeanMethods = false)
public class AssessmentTraceConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssessmentTraceConfiguration.class);

    @Bean
    AssessmentTrace assessmentTrace(@Value("${app.assessment-trace.enabled:false}") boolean enabled,
            @Value("${app.assessment-trace.execution-mode:}") String executionMode,
            ObjectProvider<AssessmentTraceSink> sink) {
        if (!enabled) {
            return AssessmentTrace.disabled();
        }
        LOGGER.warn("Assessment tracing is ENABLED. Logs will contain user-entered assessment data and complete "
                + "AI prompts and responses. Use only for controlled development and diagnosis.");
        return AssessmentTrace.enabled(executionMode, sink.getIfAvailable());
    }

    /** Adds the wire-level narrative trace to every ChatClient, only while tracing is enabled. */
    @Bean
    @ConditionalOnProperty(prefix = "app.assessment-trace", name = "enabled", havingValue = "true")
    ChatClientCustomizer assessmentTraceChatClientCustomizer(AssessmentTrace trace) {
        return builder -> builder.defaultAdvisors(new AssessmentTraceAdvisor(trace));
    }
}
