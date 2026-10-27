package com.chikere.futureai.narrative.professional.paid.persistence;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeStore;
import com.chikere.futureai.narrative.professional.paid.StoredProfessionalPaidNarrative;
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

/** Insert-once paid-narrative storage; the unique assessment constraint makes one outcome canonical. */
@Repository
class JpaProfessionalPaidNarrativeStore implements ProfessionalPaidNarrativeStore {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ProfessionalPaidNarrativeJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaProfessionalPaidNarrativeStore(ProfessionalPaidNarrativeJpaRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<StoredProfessionalPaidNarrative> findByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> repository.findByAssessmentId(assessmentId)
                .map(JpaProfessionalPaidNarrativeStore::toStored));
    }

    @Override
    public StoredProfessionalPaidNarrative saveIfAbsent(StoredProfessionalPaidNarrative narrative) {
        try {
            newTransaction.executeWithoutResult(status -> {
                var entity = new ProfessionalPaidNarrativeEntity();
                entity.id = narrative.narrativeId();
                entity.assessmentId = narrative.assessmentId();
                entity.analysisId = narrative.analysisId();
                entity.guidanceId = narrative.guidanceId();
                entity.narrativeVersion = narrative.narrativeVersion();
                entity.status = narrative.status().name();
                entity.provider = narrative.provider();
                entity.model = narrative.model().orElse(null);
                entity.providerRequestId = narrative.providerRequestId().orElse(null);
                entity.attempts = narrative.attempts();
                entity.fallbackReason = narrative.fallbackReason().orElse(null);
                entity.generatedAt = narrative.generatedAt();
                entity.narrative = narrative.narrative().map(value -> (com.fasterxml.jackson.databind.JsonNode)
                        JSON.valueToTree(value)).orElse(null);
                entityManager.persist(entity);
                entityManager.flush();
            });
        }
        catch (DataIntegrityViolationException | PersistenceException duplicateOrInvalid) {
            return findByAssessmentId(narrative.assessmentId()).orElseThrow(() -> duplicateOrInvalid);
        }
        return findByAssessmentId(narrative.assessmentId())
                .orElseThrow(() -> new IllegalStateException("the paid narrative was not persisted"));
    }

    private static StoredProfessionalPaidNarrative toStored(ProfessionalPaidNarrativeEntity entity) {
        try {
            Optional<ProfessionalPaidNarrative> narrative = entity.narrative == null ? Optional.empty()
                    : Optional.of(JSON.treeToValue(entity.narrative, ProfessionalPaidNarrative.class));
            return new StoredProfessionalPaidNarrative(entity.id, entity.assessmentId, entity.analysisId,
                    entity.guidanceId, entity.narrativeVersion, PaidNarrativeStatus.valueOf(entity.status),
                    entity.provider, Optional.ofNullable(entity.model), Optional.ofNullable(entity.providerRequestId),
                    entity.attempts, Optional.ofNullable(entity.fallbackReason), entity.generatedAt, narrative);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            throw new IllegalStateException("stored paid narrative could not be read", unreadable);
        }
    }
}
