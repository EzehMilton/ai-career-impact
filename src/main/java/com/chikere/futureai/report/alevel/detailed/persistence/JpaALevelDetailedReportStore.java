package com.chikere.futureai.report.alevel.detailed.persistence;

import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReport;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportSnapshot;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportStore;
import com.fasterxml.jackson.core.JsonProcessingException;
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

/** Insert-once a-level-report-v2 storage; the unique assessment constraint makes one snapshot canonical. */
@Repository
class JpaALevelDetailedReportStore implements ALevelDetailedReportStore {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ALevelDetailedReportJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaALevelDetailedReportStore(ALevelDetailedReportJpaRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<ALevelDetailedReportSnapshot> findByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> repository.findByAssessmentId(assessmentId)
                .map(JpaALevelDetailedReportStore::toSnapshot));
    }

    @Override
    public ALevelDetailedReportSnapshot saveIfAbsent(ALevelDetailedReportSnapshot snapshot) {
        if (!ALevelReportVersions.V2.equals(snapshot.reportVersion())) {
            throw new IllegalArgumentException("only a-level-report-v2 is stored here: " + snapshot.reportVersion());
        }
        try {
            newTransaction.executeWithoutResult(status -> {
                var entity = new ALevelDetailedReportEntity();
                entity.id = snapshot.reportId();
                entity.assessmentId = snapshot.assessmentId();
                entity.entitlementId = snapshot.entitlementId();
                entity.judgmentSchemaVersion = snapshot.judgmentSchemaVersion();
                entity.decisionPolicyVersion = snapshot.decisionPolicyVersion();
                entity.guidancePolicyVersion = snapshot.guidancePolicyVersion();
                entity.paidNarrativeVersion = snapshot.paidNarrativeVersion();
                entity.paidNarrativeStatus = snapshot.paidNarrativeStatus().name();
                entity.evidenceVersion = snapshot.evidenceVersion().orElse(null);
                entity.reportVersion = snapshot.reportVersion();
                entity.pdfVersion = snapshot.pdfVersion();
                entity.generatedAt = snapshot.generatedAt();
                entity.report = JSON.valueToTree(snapshot.report());
                entityManager.persist(entity);
                entityManager.flush();
            });
        }
        catch (DataIntegrityViolationException | PersistenceException duplicateOrInvalid) {
            return findByAssessmentId(snapshot.assessmentId()).orElseThrow(() -> duplicateOrInvalid);
        }
        return findByAssessmentId(snapshot.assessmentId())
                .orElseThrow(() -> new IllegalStateException("the a-level detailed report was not persisted"));
    }

    /** Read back exactly as stored: the report document is never re-assembled under today's code. */
    private static ALevelDetailedReportSnapshot toSnapshot(ALevelDetailedReportEntity entity) {
        try {
            return new ALevelDetailedReportSnapshot(entity.id, entity.assessmentId, entity.entitlementId,
                    entity.judgmentSchemaVersion, entity.decisionPolicyVersion, entity.guidancePolicyVersion,
                    entity.paidNarrativeVersion, ALevelPaidNarrativeStatus.valueOf(entity.paidNarrativeStatus),
                    java.util.Optional.ofNullable(entity.evidenceVersion), entity.reportVersion, entity.pdfVersion,
                    entity.generatedAt, JSON.treeToValue(entity.report, ALevelDetailedReport.class));
        }
        catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("stored a-level detailed report could not be read", unreadable);
        }
    }
}
