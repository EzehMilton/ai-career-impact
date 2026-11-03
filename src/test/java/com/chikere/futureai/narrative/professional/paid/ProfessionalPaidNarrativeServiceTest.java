package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator.GeneratedPaidNarrative;
import com.chikere.futureai.report.professional.detailed.DetailedReportFixtures;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** The paid-narrative lifecycle with a scripted generator: no OpenAI call is ever made. */
class ProfessionalPaidNarrativeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T11:00:00Z"), ZoneOffset.UTC);

    private final ProfessionalPaidNarrativeContext context = DetailedReportFixtures.chain("registered-nurse-8").context();
    private final ProfessionalPaidNarrative valid = DetailedReportFixtures.validNarrative(context);
    private final InMemoryStore store = new InMemoryStore();

    @Test
    void withoutAProviderTheResultIsARetryableFallbackAndNothingIsStoredOrCalled() {
        var result = service(Optional.empty(), AssessmentTrace.disabled()).generateOrGet(context);
        assertThat(result.status()).isEqualTo(PaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(result.retryReason()).contains("PROVIDER_NOT_CONFIGURED");
        assertThat(result.attempts()).isZero();
        assertThat(store.stored).isEmpty();
    }

    @Test
    void aValidFirstDraftIsStoredOnceWithItsProvenanceAndReusedWithoutAnotherCall() {
        var generator = new Scripted(attempt -> valid);
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var service = service(Optional.of(generator), AssessmentTrace.enabled());
            var first = service.generateOrGet(context).stored().orElseThrow();
            var again = service.generateOrGet(context).stored().orElseThrow();
            assertThat(first.status()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
            assertThat(first.narrative()).contains(valid);
            assertThat(first.attempts()).isEqualTo(1);
            assertThat(first.providerRequestId()).contains("req_1");
            assertThat(first.model()).contains("scripted-model");
            assertThat(first.narrativeVersion()).isEqualTo("professional-paid-narrative-v2");
            assertThat(again).isEqualTo(first);
            assertThat(generator.calls).isEqualTo(1);
            assertThat(log.stages()).containsSubsequence("PAID_NARRATIVE_REQUEST", "PAID_NARRATIVE_RESPONSE",
                    "PAID_NARRATIVE_VALIDATION", "PAID_NARRATIVE_REUSED");
            assertThat(log.only(TraceStage.PAID_NARRATIVE_REUSED).at("/payload/providerCalled").asBoolean()).isFalse();
        }
    }

    @Test
    void aMalformedDraftGetsExactlyOneRepairWithTheFailedRule() {
        var malformed = Narratives.opportunities(valid, valid.opportunityInsights().subList(0, 1));
        var generator = new Scripted(attempt -> attempt == 1 ? malformed : valid);
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var stored = service(Optional.of(generator), AssessmentTrace.enabled()).generateOrGet(context).stored().orElseThrow();
            assertThat(stored.status()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
            assertThat(stored.attempts()).isEqualTo(2);
            assertThat(generator.calls).isEqualTo(2);
            assertThat(generator.feedback).singleElement().satisfies(feedback ->
                    assertThat(feedback.rule()).isEqualTo(ProfessionalPaidNarrativeValidator.IDS));
            assertThat(log.only(TraceStage.PAID_NARRATIVE_REPAIR).at("/payload/failedRule").asText())
                    .isEqualTo(ProfessionalPaidNarrativeValidator.IDS);
        }
    }

    @Test
    void contentThatStillFailsAfterItsRepairBecomesTheImmutableFinalFallbackAndIsNeverRetried() {
        var invalid = Narratives.summary(valid, valid.executiveSummary() + " Your job is at risk.");
        var failing = new Scripted(attempt -> invalid);
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var result = service(Optional.of(failing), AssessmentTrace.enabled()).generateOrGet(context);
            assertThat(result.status()).isEqualTo(PaidNarrativeStatus.FALLBACK);
            var stored = result.stored().orElseThrow();
            assertThat(stored.fallbackReason()).contains("VALIDATION_FAILED_AFTER_REPAIR:"
                    + "NO_JOB_LOSS_OR_SECURITY_INFERENCE>NO_JOB_LOSS_OR_SECURITY_INFERENCE");
            assertThat(stored.narrative()).isEmpty();
            assertThat(failing.calls).isEqualTo(2);
            assertThat(log.only(TraceStage.PAID_NARRATIVE_FALLBACK).at("/payload/stored").asBoolean()).isTrue();
        }
        // Later requests, even with a provider that would now succeed, return the stored final fallback unchanged.
        var working = new Scripted(attempt -> valid);
        var later = service(Optional.of(working), AssessmentTrace.disabled()).generateOrGet(context);
        assertThat(later.status()).isEqualTo(PaidNarrativeStatus.FALLBACK);
        assertThat(later.stored()).isEqualTo(Optional.of(store.stored.get(context.assessmentId())));
        assertThat(working.calls).isZero();
    }

    @Test
    void aTransientProviderFailureIsRetryableNeverStoredAndALaterExplicitRequestCanSucceed() {
        List<RuntimeException> outages = List.of(new IllegalStateException("timeout"),
                new java.io.UncheckedIOException(new java.net.SocketTimeoutException("read timed out")),
                new org.springframework.web.client.ResourceAccessException("connection refused"),
                new RuntimeException("429 rate limit"), new RuntimeException("503 service unavailable"));
        for (RuntimeException outage : outages) {
            var failing = new Scripted(attempt -> {
                throw outage;
            });
            try (TraceLogCapture log = TraceLogCapture.start()) {
                var result = service(Optional.of(failing), AssessmentTrace.enabled()).generateOrGet(context);
                assertThat(result.status()).as(outage.getMessage()).isEqualTo(PaidNarrativeStatus.RETRYABLE_FALLBACK);
                assertThat(result.retryReason()).hasValueSatisfying(reason -> assertThat(reason).startsWith("PROVIDER_ERROR:"));
                assertThat(failing.calls).isEqualTo(1);
                assertThat(log.only(TraceStage.PAID_NARRATIVE_FALLBACK).at("/payload/stored").asBoolean()).isFalse();
            }
            assertThat(store.stored).isEmpty();
        }
        // The outage is over: the next explicit request produces and stores the narrative.
        var working = new Scripted(attempt -> valid);
        var recovered = service(Optional.of(working), AssessmentTrace.disabled()).generateOrGet(context);
        assertThat(recovered.status()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        assertThat(working.calls).isEqualTo(1);
    }

    @Test
    void anEmptyResponseOrAFailedRepairCallIsAlsoRetryableRatherThanFinal() {
        var empty = new Scripted(attempt -> null);
        assertThat(service(Optional.of(empty), AssessmentTrace.disabled()).generateOrGet(context).retryReason())
                .contains("EMPTY_PROVIDER_RESPONSE");
        var malformed = Narratives.opportunities(valid, List.of());
        var repairOutage = new Scripted(attempt -> {
            if (attempt == 1) {
                return malformed;
            }
            throw new IllegalStateException("timeout during repair");
        });
        var result = service(Optional.of(repairOutage), AssessmentTrace.disabled()).generateOrGet(context);
        assertThat(result.retryReason()).contains("REPAIR_PROVIDER_ERROR:IllegalStateException");
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(store.stored).isEmpty();
    }

    private ProfessionalPaidNarrativeService service(Optional<ProfessionalPaidNarrativeGenerator> generator,
            AssessmentTrace trace) {
        return new ProfessionalPaidNarrativeService(store, () -> generator, new ProfessionalPaidNarrativeValidator(trace),
                CLOCK, trace);
    }

    static final class Scripted implements ProfessionalPaidNarrativeGenerator {
        private final Function<Integer, ProfessionalPaidNarrative> answers;
        int calls;
        final List<NarrativeRepairFeedback> feedback = new ArrayList<>();

        Scripted(Function<Integer, ProfessionalPaidNarrative> answers) {
            this.answers = answers;
        }

        @Override
        public GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context) {
            calls++;
            return new GeneratedPaidNarrative(answers.apply(calls), Optional.of("req_" + calls), Optional.of("scripted-model"));
        }

        @Override
        public GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback) {
            this.feedback.add(feedback);
            return generate(context);
        }

        @Override
        public String providerIdentifier() {
            return "scripted";
        }

        @Override
        public Optional<String> modelIdentifier() {
            return Optional.empty();
        }
    }

    static final class InMemoryStore implements ProfessionalPaidNarrativeStore {
        final Map<UUID, StoredProfessionalPaidNarrative> stored = new HashMap<>();

        @Override
        public Optional<StoredProfessionalPaidNarrative> findByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(stored.get(assessmentId));
        }

        @Override
        public StoredProfessionalPaidNarrative saveIfAbsent(StoredProfessionalPaidNarrative narrative) {
            return stored.computeIfAbsent(narrative.assessmentId(), key -> narrative);
        }
    }
}
