package com.chikere.futureai.narrative.alevel.persistence;

import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaALevelNarrativeStore implements ALevelNarrativeStore {

    private final ALevelNarrativeJpaRepository repository;

    JpaALevelNarrativeStore(ALevelNarrativeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(StoredALevelNarrative stored) {
        ALevelNarrativeEntity entity = repository.findById(stored.assessmentId())
                .orElseGet(ALevelNarrativeEntity::new);
        entity.assessmentId = stored.assessmentId();
        entity.status = stored.status();
        entity.source = stored.source();
        entity.providerIdentifier = stored.providerIdentifier();
        entity.modelIdentifier = stored.modelIdentifier().orElse(null);
        entity.promptSchemaVersion = stored.promptSchemaVersion();
        entity.attemptedAt = stored.attemptedAt();
        entity.generatedAt = stored.generatedAt().orElse(null);
        if (stored.narrative().isPresent()) {
            ALevelNarrative narrative = stored.narrative().orElseThrow();
            entity.headline = narrative.headline();
            entity.summary = narrative.summary();
            entity.whatThisMeans = narrative.whatThisMeans();
            entity.futureOptionalityExplanation = narrative.futureOptionalityExplanation();
            entity.strengthsExplanation = narrative.strengthsExplanation();
            entity.degreePathwayExplanation = narrative.degreePathwayExplanation();
            entity.analyticalFoundationExplanation = narrative.analyticalFoundationExplanation();
            entity.humanAndCreativeExplanation = narrative.humanAndCreativeExplanation();
            entity.transferabilityExplanation = narrative.transferabilityExplanation();
            entity.adaptabilityExplanation = narrative.adaptabilityExplanation();
            entity.developmentOpportunitiesExplanation = narrative.developmentOpportunitiesExplanation();
            entity.practicalActions = narrative.practicalActions();
            entity.cautionOrLimitations = narrative.cautionOrLimitations();
        }
        else {
            entity.headline = null;
            entity.summary = null;
            entity.whatThisMeans = null;
            entity.futureOptionalityExplanation = null;
            entity.strengthsExplanation = null;
            entity.degreePathwayExplanation = null;
            entity.analyticalFoundationExplanation = null;
            entity.humanAndCreativeExplanation = null;
            entity.transferabilityExplanation = null;
            entity.adaptabilityExplanation = null;
            entity.developmentOpportunitiesExplanation = null;
            entity.practicalActions = null;
            entity.cautionOrLimitations = null;
        }
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredALevelNarrative> findByAssessmentId(UUID assessmentId) {
        return repository.findById(assessmentId).map(this::toStored);
    }

    private StoredALevelNarrative toStored(ALevelNarrativeEntity entity) {
        Optional<ALevelNarrative> narrative = entity.status == NarrativeStatus.AVAILABLE
                ? Optional.of(new ALevelNarrative(entity.headline, entity.summary, entity.whatThisMeans,
                        entity.futureOptionalityExplanation, entity.strengthsExplanation,
                        entity.degreePathwayExplanation, entity.analyticalFoundationExplanation,
                        entity.humanAndCreativeExplanation, entity.transferabilityExplanation,
                        entity.adaptabilityExplanation, entity.developmentOpportunitiesExplanation,
                        entity.practicalActions, entity.cautionOrLimitations))
                : Optional.empty();
        return new StoredALevelNarrative(entity.assessmentId, entity.status, narrative,
                entity.source, entity.providerIdentifier, Optional.ofNullable(entity.modelIdentifier),
                entity.promptSchemaVersion, entity.attemptedAt, Optional.ofNullable(entity.generatedAt));
    }
}
