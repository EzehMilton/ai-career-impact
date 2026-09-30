package com.chikere.futureai.narrative.professional.persistence;

import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalNarrativeStore implements ProfessionalNarrativeStore {

    private final ProfessionalNarrativeJpaRepository repository;

    JpaProfessionalNarrativeStore(ProfessionalNarrativeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(StoredProfessionalNarrative stored) {
        ProfessionalNarrativeEntity entity = repository.findById(stored.assessmentId())
                .orElseGet(ProfessionalNarrativeEntity::new);
        entity.assessmentId = stored.assessmentId();
        entity.status = stored.status();
        entity.source = stored.source();
        entity.providerIdentifier = stored.providerIdentifier();
        entity.modelIdentifier = stored.modelIdentifier().orElse(null);
        entity.promptSchemaVersion = stored.promptSchemaVersion();
        entity.attemptedAt = stored.attemptedAt();
        entity.generatedAt = stored.generatedAt().orElse(null);
        if (stored.narrative().isPresent()) {
            ProfessionalNarrative narrative = stored.narrative().orElseThrow();
            entity.headline = narrative.headline();
            entity.summary = narrative.summary();
            entity.whatThisMeans = narrative.whatThisMeans();
            entity.exposureExplanation = narrative.exposureExplanation();
            entity.humanAdvantageExplanation = narrative.humanAdvantageExplanation();
            entity.complementarityExplanation = narrative.complementarityExplanation();
            entity.adaptabilityExplanation = narrative.adaptabilityExplanation();
            entity.practicalActions = narrative.practicalActions();
            entity.cautionOrLimitations = narrative.cautionOrLimitations();
        }
        else {
            entity.headline = null;
            entity.summary = null;
            entity.whatThisMeans = null;
            entity.exposureExplanation = null;
            entity.humanAdvantageExplanation = null;
            entity.complementarityExplanation = null;
            entity.adaptabilityExplanation = null;
            entity.practicalActions = null;
            entity.cautionOrLimitations = null;
        }
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProfessionalNarrative> findByAssessmentId(UUID assessmentId) {
        return repository.findById(assessmentId).map(this::toStored);
    }

    private StoredProfessionalNarrative toStored(ProfessionalNarrativeEntity entity) {
        Optional<ProfessionalNarrative> narrative = entity.status == NarrativeStatus.AVAILABLE
                ? Optional.of(new ProfessionalNarrative(entity.headline, entity.summary, entity.whatThisMeans,
                        entity.exposureExplanation, entity.humanAdvantageExplanation,
                        entity.complementarityExplanation, entity.adaptabilityExplanation,
                        entity.practicalActions, entity.cautionOrLimitations))
                : Optional.empty();
        return new StoredProfessionalNarrative(entity.assessmentId, entity.status, narrative,
                entity.source, entity.providerIdentifier, Optional.ofNullable(entity.modelIdentifier),
                entity.promptSchemaVersion, entity.attemptedAt, Optional.ofNullable(entity.generatedAt));
    }
}
