package com.chikere.futureai.assessment.university.persistence;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUniversityAssessmentStore implements UniversityAssessmentStore {
    private final UniversityAssessmentJpaRepository repository;
    private final UniversityAssessmentPersistenceMapper mapper;
    private final EntityManager entityManager;

    JpaUniversityAssessmentStore(UniversityAssessmentJpaRepository repository,
            UniversityAssessmentPersistenceMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(StoredUniversityAssessment assessment) {
        // Insert only: a completed assessment is never merged over an old snapshot.
        entityManager.persist(mapper.toEntity(assessment));
        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredUniversityAssessment> findById(UUID id) {
        return repository.findById(id).map(mapper::toSnapshot);
    }
}
