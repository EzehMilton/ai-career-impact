package com.chikere.futureai.assessment.professional.persistence;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalAssessmentStore implements ProfessionalAssessmentStore {
    private final ProfessionalAssessmentJpaRepository repository;
    private final ProfessionalAssessmentPersistenceMapper mapper;
    private final EntityManager entityManager;

    JpaProfessionalAssessmentStore(ProfessionalAssessmentJpaRepository repository,
                                   ProfessionalAssessmentPersistenceMapper mapper,
                                   EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(StoredProfessionalAssessment assessment) {
        // Insert only: a completed assessment is never merged over an old snapshot.
        entityManager.persist(mapper.toEntity(assessment));
        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProfessionalAssessment> findById(UUID id) {
        return repository.findById(id).map(mapper::toSnapshot);
    }
}
