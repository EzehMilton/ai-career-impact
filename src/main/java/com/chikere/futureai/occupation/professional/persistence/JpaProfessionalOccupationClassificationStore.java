package com.chikere.futureai.occupation.professional.persistence;

import com.chikere.futureai.occupation.professional.application.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalOccupationClassificationStore implements ProfessionalOccupationClassificationStore {
    private final ProfessionalOccupationClassificationJpaRepository repository;

    JpaProfessionalOccupationClassificationStore(ProfessionalOccupationClassificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(StoredProfessionalOccupationClassification stored) {
        if (repository.existsById(stored.assessmentId())) {
            throw new IllegalStateException("occupation classification is an immutable historical snapshot");
        }
        var value = stored.classification();
        var entity = new ProfessionalOccupationClassificationEntity();
        entity.assessmentId = stored.assessmentId();
        entity.canonicalOccupation = value.canonicalOccupation();
        entity.classificationSystem = value.classificationSystem();
        entity.classificationCode = value.classificationCode();
        entity.source = value.source();
        entity.quality = value.quality();
        entity.classifierIdentifier = value.classifierIdentifier();
        entity.classifierVersion = value.classifierVersion();
        entity.classifiedAt = stored.classifiedAt();
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProfessionalOccupationClassification> findByAssessmentId(UUID assessmentId) {
        return repository.findById(assessmentId).map(entity -> new StoredProfessionalOccupationClassification(
                entity.assessmentId,
                new ProfessionalOccupationClassification(entity.canonicalOccupation, entity.classificationSystem,
                        entity.classificationCode, entity.source, entity.quality,
                        entity.classifierIdentifier, entity.classifierVersion),
                entity.classifiedAt));
    }
}
