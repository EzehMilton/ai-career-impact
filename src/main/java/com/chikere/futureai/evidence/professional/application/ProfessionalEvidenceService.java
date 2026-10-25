package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalEvidenceService implements ProfessionalEvidenceGathering {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalEvidenceService.class);
    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalEvidenceStore evidenceStore;
    private final Optional<ProfessionalEvidenceProvider> provider;
    private final ProfessionalOccupationClassificationStore classificationStore;
    private final ProfessionalEvidenceRequestFactory requestFactory;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public ProfessionalEvidenceService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore, Optional<ProfessionalEvidenceProvider> provider,
            ProfessionalOccupationClassificationStore classificationStore,
            ProfessionalEvidenceRequestFactory requestFactory, Clock clock) {
        this(assessmentStore, evidenceStore, provider, classificationStore, requestFactory, clock, JourneyMetrics.disabled());
    }

    public ProfessionalEvidenceService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore, Optional<ProfessionalEvidenceProvider> provider,
            ProfessionalOccupationClassificationStore classificationStore,
            ProfessionalEvidenceRequestFactory requestFactory, Clock clock,
            JourneyMetrics metrics) {
        this(assessmentStore, evidenceStore, provider, classificationStore, requestFactory, clock, metrics,
                AssessmentTrace.disabled());
    }

    public ProfessionalEvidenceService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore, Optional<ProfessionalEvidenceProvider> provider,
            ProfessionalOccupationClassificationStore classificationStore,
            ProfessionalEvidenceRequestFactory requestFactory, Clock clock,
            JourneyMetrics metrics, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.provider = Objects.requireNonNull(provider);
        this.classificationStore = Objects.requireNonNull(classificationStore);
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public Optional<ProfessionalEvidenceBundle> gatherFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        Optional<ProfessionalEvidenceBundle> existing = evidenceStore.findByAssessmentId(assessmentId);
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
        evidenceStore.save(new ProfessionalEvidenceBundle(assessmentId,
                ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                configuredProvider.providerIdentifier(), EvidenceStatus.PENDING, attemptedAt, List.of()));
        try {
            var request = requestFactory.create(assessment, classificationStore.findByAssessmentId(assessmentId));
            trace.event(JourneyType.PROFESSIONAL, TraceStage.EVIDENCE_REQUEST,
                    () -> evidenceRequest(configuredProvider, request));
            ProfessionalEvidenceBundle gathered = metrics.timed("evidence", "professional",
                    () -> trace.timed(JourneyType.PROFESSIONAL, TraceStage.EVIDENCE_RESPONSE, "evidenceMs",
                            () -> configuredProvider.gather(request), bundle -> bundle));
            validateProviderResult(assessmentId, configuredProvider, gathered);
            evidenceStore.save(gathered);
            LOGGER.info("Evidence journey=professional assessmentId={} outcome={} itemCount={}",
                    assessmentId, gathered.status(), gathered.items().size());
            return Optional.of(gathered);
        } catch (RuntimeException failure) {
            var failed = new ProfessionalEvidenceBundle(assessmentId,
                    ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                    configuredProvider.providerIdentifier(), EvidenceStatus.FAILED, attemptedAt, List.of());
            evidenceStore.save(failed);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.EVIDENCE_RESPONSE, () -> Map.of(
                    "status", "STORED_AS_FAILED", "errorType", failure.getClass().getName(),
                    "errorMessage", AssessmentTrace.safeMessage(failure)));
            LOGGER.warn("Evidence journey=professional assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, failure.getClass().getSimpleName());
            return Optional.of(failed);
        }
    }

    public Optional<ProfessionalEvidenceBundle> findFor(UUID assessmentId) {
        return evidenceStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private void validateProviderResult(UUID assessmentId, ProfessionalEvidenceProvider expected,
            ProfessionalEvidenceBundle bundle) {
        Objects.requireNonNull(bundle, "provider returned no evidence bundle");
        if (!assessmentId.equals(bundle.assessmentId())
                || bundle.source() != expected.source()
                || !bundle.providerIdentifier().equals(expected.providerIdentifier())
                || !bundle.evidenceVersion().equals(ProfessionalEvidenceVersions.EVIDENCE_SCHEMA)
                || (bundle.status() != EvidenceStatus.AVAILABLE && bundle.status() != EvidenceStatus.PARTIAL)) {
            throw new IllegalArgumentException("provider returned inconsistent evidence metadata");
        }
    }

    private Map<String,
            Object> evidenceRequest(ProfessionalEvidenceProvider provider, ProfessionalEvidenceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", provider.source());
        payload.put("providerIdentifier", provider.providerIdentifier());
        payload.put("providerImplementation", provider.getClass().getName());
        payload.put("request", request);
        return payload;
    }

    private void traceSkipped(String reason) {
        trace.event(JourneyType.PROFESSIONAL, TraceStage.EVIDENCE_RESPONSE,
                () -> Map.of("status", "SKIPPED", "reason", reason));
    }
}
