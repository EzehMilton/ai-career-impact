package com.chikere.futureai.narrative.university.persistence;

import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUniversityNarrativeStore implements UniversityNarrativeStore {

    private final UniversityNarrativeJpaRepository repository;

    JpaUniversityNarrativeStore(UniversityNarrativeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(StoredUniversityNarrative stored) {
        UniversityNarrativeEntity entity = repository.findById(stored.assessmentId())
                .orElseGet(UniversityNarrativeEntity::new);
        entity.assessmentId = stored.assessmentId();
        entity.status = stored.status();
        entity.source = stored.source();
        entity.providerIdentifier = stored.providerIdentifier();
        entity.modelIdentifier = stored.modelIdentifier().orElse(null);
        entity.promptSchemaVersion = stored.promptSchemaVersion();
        entity.attemptedAt = stored.attemptedAt();
        entity.generatedAt = stored.generatedAt().orElse(null);
        if (stored.narrative().isPresent()) {
            UniversityNarrative narrative = stored.narrative().orElseThrow();
            entity.headline = narrative.headline();
            entity.summary = narrative.summary();
            entity.whatThisMeans = narrative.whatThisMeans();
            entity.futureReadinessExplanation = narrative.futureReadinessExplanation();
            entity.strengthsExplanation = narrative.strengthsExplanation();
            entity.developmentOpportunitiesExplanation = narrative.developmentOpportunitiesExplanation();
            entity.complementarityExplanation = narrative.complementarityExplanation();
            entity.transferableSkillsExplanation = narrative.transferableSkillsExplanation();
            entity.careerAdaptabilityExplanation = narrative.careerAdaptabilityExplanation();
            entity.practicalActions = narrative.practicalActions();
            entity.cautionOrLimitations = narrative.cautionOrLimitations();
        }
        else {
            entity.headline = null;
            entity.summary = null;
            entity.whatThisMeans = null;
            entity.futureReadinessExplanation = null;
            entity.strengthsExplanation = null;
            entity.developmentOpportunitiesExplanation = null;
            entity.complementarityExplanation = null;
            entity.transferableSkillsExplanation = null;
            entity.careerAdaptabilityExplanation = null;
            entity.practicalActions = null;
            entity.cautionOrLimitations = null;
        }
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredUniversityNarrative> findByAssessmentId(UUID assessmentId) {
        return repository.findById(assessmentId).map(this::toStored);
    }

    private StoredUniversityNarrative toStored(UniversityNarrativeEntity entity) {
        Optional<UniversityNarrative> narrative = entity.status == NarrativeStatus.AVAILABLE
                ? Optional.of(new UniversityNarrative(entity.headline, entity.summary, entity.whatThisMeans,
                        entity.futureReadinessExplanation, entity.strengthsExplanation,
                        entity.developmentOpportunitiesExplanation, entity.complementarityExplanation,
                        entity.transferableSkillsExplanation, entity.careerAdaptabilityExplanation,
                        entity.practicalActions, entity.cautionOrLimitations))
                : Optional.empty();
        return new StoredUniversityNarrative(entity.assessmentId, entity.status, narrative,
                entity.source, entity.providerIdentifier, Optional.ofNullable(entity.modelIdentifier),
                entity.promptSchemaVersion, entity.attemptedAt, Optional.ofNullable(entity.generatedAt));
    }
}
