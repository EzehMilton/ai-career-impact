package com.chikere.futureai.evidence.university.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evidence is factual context gathered after the authoritative assessment has committed.
 *
 * <p>It cannot change Future Readiness, AI Complementarity, Human Differentiation, Skill
 * Transferability, Career Adaptability, status or confidence. A provider failure is recorded as
 * {@code FAILED} and never causes a fixture lookup or any other fallback.</p>
 */
public final class UniversityEvidenceService implements UniversityEvidenceGathering {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityEvidenceService.class);
    private final UniversityAssessmentStore assessmentStore;
    private final UniversityEvidenceStore evidenceStore;
    private final Optional<UniversityEvidenceProvider> provider;
    private final UniversityEvidenceRequestFactory requestFactory;
    private final Clock clock;

    public UniversityEvidenceService(UniversityAssessmentStore assessmentStore,
            UniversityEvidenceStore evidenceStore, Optional<UniversityEvidenceProvider> provider,
            UniversityEvidenceRequestFactory requestFactory, Clock clock) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.provider = Objects.requireNonNull(provider);
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<UniversityEvidenceBundle> gatherFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        Optional<UniversityEvidenceBundle> existing = evidenceStore.findByAssessmentId(assessmentId);
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
        evidenceStore.save(new UniversityEvidenceBundle(assessmentId,
                UniversityEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                configuredProvider.providerIdentifier(), EvidenceStatus.PENDING, attemptedAt, List.of()));
        try {
            UniversityEvidenceBundle gathered = configuredProvider.gather(requestFactory.create(assessment));
            validateProviderResult(assessmentId, configuredProvider, gathered);
            evidenceStore.save(gathered);
            LOGGER.info("Evidence journey=university assessmentId={} outcome={} itemCount={}",
                    assessmentId, gathered.status(), gathered.items().size());
            return Optional.of(gathered);
        }
        catch (RuntimeException failure) {
            var failed = new UniversityEvidenceBundle(assessmentId,
                    UniversityEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                    configuredProvider.providerIdentifier(), EvidenceStatus.FAILED, attemptedAt, List.of());
            evidenceStore.save(failed);
            LOGGER.warn("Evidence journey=university assessmentId={} outcome=FAILED failureType={}",
                    assessmentId, failure.getClass().getSimpleName());
            return Optional.of(failed);
        }
    }

    public Optional<UniversityEvidenceBundle> findFor(UUID assessmentId) {
        return evidenceStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private void validateProviderResult(UUID assessmentId, UniversityEvidenceProvider expected,
            UniversityEvidenceBundle bundle) {
        Objects.requireNonNull(bundle, "provider returned no evidence bundle");
        if (!assessmentId.equals(bundle.assessmentId())
                || bundle.source() != expected.source()
                || !bundle.providerIdentifier().equals(expected.providerIdentifier())
                || !bundle.evidenceVersion().equals(UniversityEvidenceVersions.EVIDENCE_SCHEMA)
                || (bundle.status() != EvidenceStatus.AVAILABLE && bundle.status() != EvidenceStatus.PARTIAL)) {
            throw new IllegalArgumentException("provider returned inconsistent evidence metadata");
        }
    }
}
