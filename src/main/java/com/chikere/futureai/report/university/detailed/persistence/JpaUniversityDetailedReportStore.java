package com.chikere.futureai.report.university.detailed.persistence;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportSnapshot;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportStore;
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

/** Insert-once university-report-v2 storage; the unique assessment constraint makes one snapshot canonical. */
@Repository
class JpaUniversityDetailedReportStore implements UniversityDetailedReportStore {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final UniversityDetailedReportJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaUniversityDetailedReportStore(UniversityDetailedReportJpaRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<UniversityDetailedReportSnapshot> findByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> repository.findByAssessmentId(assessmentId)
                .map(JpaUniversityDetailedReportStore::toSnapshot));
    }

    @Override
    public UniversityDetailedReportSnapshot saveIfAbsent(UniversityDetailedReportSnapshot snapshot) {
        if (!UniversityReportVersions.V2.equals(snapshot.reportVersion())) {
            throw new IllegalArgumentException("only university-report-v2 is stored here: " + snapshot.reportVersion());
        }
        try {
            newTransaction.executeWithoutResult(status -> {
                var entity = new UniversityDetailedReportEntity();
                entity.id = snapshot.reportId();
                entity.assessmentId = snapshot.assessmentId();
                entity.entitlementId = snapshot.entitlementId();
                entity.decisionPolicyVersion = snapshot.decisionPolicyVersion();
                entity.moduleAnalysisId = snapshot.moduleAnalysisId();
                entity.moduleSchemaVersion = snapshot.moduleSchemaVersion();
                entity.modulePolicyVersion = snapshot.modulePolicyVersion();
                entity.guidancePolicyVersion = snapshot.guidancePolicyVersion();
                entity.paidNarrativeVersion = snapshot.paidNarrativeVersion();
                entity.paidNarrativeStatus = snapshot.paidNarrativeStatus().name();
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
                .orElseThrow(() -> new IllegalStateException("the university detailed report was not persisted"));
    }

    /** Read back exactly as stored: the report document is never re-assembled under today's code. */
    private static UniversityDetailedReportSnapshot toSnapshot(UniversityDetailedReportEntity entity) {
        try {
            return new UniversityDetailedReportSnapshot(entity.id, entity.assessmentId, entity.entitlementId,
                    entity.decisionPolicyVersion, entity.moduleAnalysisId, entity.moduleSchemaVersion,
                    entity.modulePolicyVersion, entity.guidancePolicyVersion, entity.paidNarrativeVersion,
                    UniversityPaidNarrativeStatus.valueOf(entity.paidNarrativeStatus), entity.reportVersion,
                    entity.pdfVersion, entity.generatedAt, JSON.treeToValue(entity.report, UniversityDetailedReport.class));
        }
        catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("stored university detailed report could not be read", unreadable);
        }
    }
}
