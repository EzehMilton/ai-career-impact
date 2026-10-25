package com.chikere.futureai.evidence.alevel.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evidence is factual context gathered after the authoritative assessment has committed.
 *
 * <p>It cannot change Future Optionality or any supporting measure, status or confidence. A
 * provider failure is recorded as {@code FAILED} and never causes a fixture lookup or any other
 * fallback.</p>
 */
public final class ALevelEvidenceService implements ALevelEvidenceGathering {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelEvidenceService.class);
    private final ALevelAssessmentStore assessmentStore;
    private final ALevelEvidenceStore evidenceStore;
    private final Optional<ALevelEvidenceProvider> provider;
    private final ALevelEvidenceRequestFactory requestFactory;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public ALevelEvidenceService(ALevelAssessmentStore assessmentStore, ALevelEvidenceStore evidenceStore,
            Optional<ALevelEvidenceProvider> provider, ALevelEvidenceRequestFactory requestFactory,
            Clock clock) {
        this(assessmentStore, evidenceStore, provider, requestFactory, clock, JourneyMetrics.disabled());
    }

    public ALevelEvidenceService(ALevelAssessmentStore assessmentStore, ALevelEvidenceStore evidenceStore,
            Optional<ALevelEvidenceProvider> provider, ALevelEvidenceRequestFactory requestFactory,
            Clock clock,
            JourneyMetrics metrics) {
        this(assessmentStore, evidenceStore, provider, requestFactory, clock, metrics, AssessmentTrace.disabled());
    }

    public ALevelEvidenceService(ALevelAssessmentStore assessmentStore, ALevelEvidenceStore evidenceStore,
            Optional<ALevelEvidenceProvider> provider, ALevelEvidenceRequestFactory requestFactory,
            Clock clock,
            JourneyMetrics metrics, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.provider = Objects.requireNonNull(provider);
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public Optional<ALevelEvidenceBundle> gatherFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        Optional<ALevelEvidenceBundle> existing = evidenceStore.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            traceSkipped("evidence already stored for this assessment; never refreshed here");
            return existing;
        }
        if (provider.isEmpty()) {
            traceSkipped("no evidence provider is configured, so no external evidence exists for this assessment");
            return Optional.empty();
        }
        var configuredProvider = provider.orElseThrow();
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        Instant attemptedAt = Instant.now(clock);
        evidenceStore.save(new ALevelEvidenceBundle(assessmentId,
                ALevelEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                configuredProvider.providerIdentifier(), EvidenceStatus.PENDING, attemptedAt, List.of()));
        try {
            var request = requestFactory.create(assessment);
            trace.event(JourneyType.A_LEVEL, TraceStage.EVIDENCE_REQUEST,
                    () -> evidenceRequest(configuredProvider, request));
            ALevelEvidenceBundle gathered = metrics.timed("evidence", "a-level",
                    () -> trace.timed(JourneyType.A_LEVEL, TraceStage.EVIDENCE_RESPONSE, "evidenceMs",
                            () -> configuredProvider.gather(request), bundle -> bundle));
            validateProviderResult(assessmentId, configuredProvider, gathered);
            evidenceStore.save(gathered);
            LOGGER.info("Evidence journey=alevel assessmentId={} outcome={} itemCount={}",
                    assessmentId, gathered.status(), gathered.items().size());
            return Optional.of(gathered);
        }
        catch (RuntimeException failure) {
            var failed = new ALevelEvidenceBundle(assessmentId, ALevelEvidenceVersions.EVIDENCE_SCHEMA,
                    configuredProvider.source(), configuredProvider.providerIdentifier(),
                    EvidenceStatus.FAILED, attemptedAt, List.of());
            evidenceStore.save(failed);
            trace.event(JourneyType.A_LEVEL, TraceStage.EVIDENCE_RESPONSE, () -> Map.of(
                    "status", "STORED_AS_FAILED", "errorType", failure.getClass().getName(),
                    "errorMessage", AssessmentTrace.safeMessage(failure)));
            LOGGER.warn("Evidence journey=alevel assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, failure.getClass().getSimpleName());
            return Optional.of(failed);
        }
    }

    public Optional<ALevelEvidenceBundle> findFor(UUID assessmentId) {
        return evidenceStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private void validateProviderResult(UUID assessmentId, ALevelEvidenceProvider expected,
            ALevelEvidenceBundle bundle) {
        Objects.requireNonNull(bundle, "provider returned no evidence bundle");
        if (!assessmentId.equals(bundle.assessmentId())
                || bundle.source() != expected.source()
                || !bundle.providerIdentifier().equals(expected.providerIdentifier())
                || !bundle.evidenceVersion().equals(ALevelEvidenceVersions.EVIDENCE_SCHEMA)
                || (bundle.status() != EvidenceStatus.AVAILABLE && bundle.status() != EvidenceStatus.PARTIAL)) {
            throw new IllegalArgumentException("provider returned inconsistent evidence metadata");
        }
    }

    private Map<String, Object> evidenceRequest(ALevelEvidenceProvider provider, ALevelEvidenceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", provider.source());
        payload.put("providerIdentifier", provider.providerIdentifier());
        payload.put("providerImplementation", provider.getClass().getName());
        payload.put("request", request);
        return payload;
    }

    private void traceSkipped(String reason) {
        trace.event(JourneyType.A_LEVEL, TraceStage.EVIDENCE_RESPONSE,
                () -> Map.of("status", "SKIPPED", "reason", reason));
    }
}
