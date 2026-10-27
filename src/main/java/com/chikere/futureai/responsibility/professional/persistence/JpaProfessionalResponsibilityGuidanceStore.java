package com.chikere.futureai.responsibility.professional.persistence;

import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/** Insert-once guidance storage; the unique assessment and analysis constraints make one snapshot canonical. */
@Repository
class JpaProfessionalResponsibilityGuidanceStore implements ProfessionalResponsibilityGuidanceStore {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ProfessionalResponsibilityGuidanceJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaProfessionalResponsibilityGuidanceStore(ProfessionalResponsibilityGuidanceJpaRepository repository,
            EntityManager entityManager, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<ProfessionalResponsibilityGuidanceSnapshot> findByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> repository.findByAssessmentId(assessmentId).map(JpaProfessionalResponsibilityGuidanceStore::toSnapshot));
    }

    @Override
    public ProfessionalResponsibilityGuidanceSnapshot saveIfAbsent(ProfessionalResponsibilityGuidanceSnapshot snapshot) {
        try {
            newTransaction.executeWithoutResult(status -> {
                var entity = new ProfessionalResponsibilityGuidanceEntity();
                entity.id = snapshot.guidanceId();
                entity.assessmentId = snapshot.assessmentId();
                entity.analysisId = snapshot.analysisSnapshotId();
                entity.policyVersion = snapshot.policyVersion();
                entity.createdAt = snapshot.createdAt();
                entity.guidance = JSON.valueToTree(snapshot.guidance());
                entityManager.persist(entity);
                entityManager.flush();
            });
        }
        catch (DataIntegrityViolationException | PersistenceException duplicateOrInvalid) {
            // Not yet translated inside this repository, so both forms are caught; the winner, if any, is returned.
            return findByAssessmentId(snapshot.assessmentId()).orElseThrow(() -> duplicateOrInvalid);
        }
        return findByAssessmentId(snapshot.assessmentId())
                .orElseThrow(() -> new IllegalStateException("the guidance was not persisted"));
    }

    private static ProfessionalResponsibilityGuidanceSnapshot toSnapshot(ProfessionalResponsibilityGuidanceEntity entity) {
        try {
            return new ProfessionalResponsibilityGuidanceSnapshot(entity.id, entity.assessmentId, entity.analysisId,
                    entity.policyVersion, entity.createdAt,
                    JSON.treeToValue(entity.guidance, ProfessionalResponsibilityGuidance.class));
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            throw new IllegalStateException("stored guidance could not be read", unreadable);
        }
    }
}
