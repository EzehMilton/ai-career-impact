package com.chikere.futureai.evidence.alevel.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    public ALevelEvidenceService(ALevelAssessmentStore assessmentStore, ALevelEvidenceStore evidenceStore,
            Optional<ALevelEvidenceProvider> provider, ALevelEvidenceRequestFactory requestFactory,
            Clock clock) {
        this(assessmentStore, evidenceStore, provider, requestFactory, clock, JourneyMetrics.disabled());
    }

    public ALevelEvidenceService(ALevelAssessmentStore assessmentStore, ALevelEvidenceStore evidenceStore,
            Optional<ALevelEvidenceProvider> provider, ALevelEvidenceRequestFactory requestFactory,
            Clock clock,
            JourneyMetrics metrics) {
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
            // Historical evidence, including FAILED, PARTIAL and older versions, is never refreshed here.
            return existing;
        }
        if (provider.isEmpty()) {
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
            ALevelEvidenceBundle gathered = metrics.timed("evidence", "a-level",
                    () -> configuredProvider.gather(request));
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
}
