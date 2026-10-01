package com.chikere.futureai.assessment.alevel.persistence;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaALevelAssessmentStore implements ALevelAssessmentStore {
    private final ALevelAssessmentJpaRepository repository;
    private final ALevelAssessmentPersistenceMapper mapper;
    private final EntityManager entityManager;

    JpaALevelAssessmentStore(ALevelAssessmentJpaRepository repository,
            ALevelAssessmentPersistenceMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(StoredALevelAssessment assessment) {
        // Insert only: a completed assessment is never merged over an old snapshot.
        entityManager.persist(mapper.toEntity(assessment));
        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredALevelAssessment> findById(UUID id) {
        return repository.findById(id).map(mapper::toSnapshot);
    }
}
