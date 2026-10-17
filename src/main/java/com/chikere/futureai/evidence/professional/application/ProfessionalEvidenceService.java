package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
        if (existing.isPresent()) return existing;
        if (provider.isEmpty()) return Optional.empty();
        var configuredProvider = provider.orElseThrow();
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        Instant attemptedAt = Instant.now(clock);
        evidenceStore.save(new ProfessionalEvidenceBundle(assessmentId,
                ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                configuredProvider.providerIdentifier(), EvidenceStatus.PENDING, attemptedAt, List.of()));
        try {
            var request = requestFactory.create(assessment, classificationStore.findByAssessmentId(assessmentId));
            ProfessionalEvidenceBundle gathered = metrics.timed("evidence", "professional",
                    () -> configuredProvider.gather(request));
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
}
