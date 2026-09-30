package com.chikere.futureai.evidence.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalEvidenceService {
    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalEvidenceStore evidenceStore;
    private final Optional<ProfessionalEvidenceProvider> provider;
    private final ProfessionalEvidenceRequestFactory requestFactory;
    private final Clock clock;

    public ProfessionalEvidenceService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore, Optional<ProfessionalEvidenceProvider> provider,
            ProfessionalEvidenceRequestFactory requestFactory, Clock clock) {
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.evidenceStore = Objects.requireNonNull(evidenceStore);
        this.provider = Objects.requireNonNull(provider);
        this.requestFactory = Objects.requireNonNull(requestFactory);
        this.clock = Objects.requireNonNull(clock);
    }

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
            ProfessionalEvidenceBundle gathered = configuredProvider.gather(requestFactory.create(assessment));
            validateProviderResult(assessmentId, configuredProvider, gathered);
            evidenceStore.save(gathered);
            return Optional.of(gathered);
        } catch (RuntimeException failure) {
            var failed = new ProfessionalEvidenceBundle(assessmentId,
                    ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, configuredProvider.source(),
                    configuredProvider.providerIdentifier(), EvidenceStatus.FAILED, attemptedAt, List.of());
            evidenceStore.save(failed);
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
