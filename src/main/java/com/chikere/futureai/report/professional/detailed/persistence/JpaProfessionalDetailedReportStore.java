package com.chikere.futureai.report.professional.detailed.persistence;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportSnapshot;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportStore;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportVersions;
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

/** Insert-once detailed-report storage; the unique assessment constraint makes one snapshot canonical. */
@Repository
class JpaProfessionalDetailedReportStore implements ProfessionalDetailedReportStore {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final ProfessionalDetailedReportJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaProfessionalDetailedReportStore(ProfessionalDetailedReportJpaRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<ProfessionalDetailedReportSnapshot> findByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> repository.findByAssessmentId(assessmentId)
                .map(JpaProfessionalDetailedReportStore::toSnapshot));
    }

    @Override
    public ProfessionalDetailedReportSnapshot saveIfAbsent(ProfessionalDetailedReportSnapshot snapshot) {
        if (!ProfessionalDetailedReportVersions.REPORT.equals(snapshot.reportVersion())) {
            throw new IllegalArgumentException("only the current detailed report version is stored: "
                    + snapshot.reportVersion());
        }
        try {
            newTransaction.executeWithoutResult(status -> {
                var provenance = snapshot.report().provenance();
                var entity = new ProfessionalDetailedReportEntity();
                entity.id = snapshot.reportId();
                entity.assessmentId = snapshot.assessmentId();
                entity.entitlementId = snapshot.entitlementId();
                entity.roleReportVersion = provenance.roleReportVersion();
                entity.decisionPolicyVersion = provenance.decisionPolicyVersion();
                entity.analysisId = snapshot.analysisId();
                entity.responsibilitySchemaVersion = provenance.responsibilitySchemaVersion();
                entity.responsibilityPolicyVersion = provenance.responsibilityPolicyVersion();
                entity.guidanceId = snapshot.guidanceId();
                entity.guidancePolicyVersion = provenance.guidancePolicyVersion();
                entity.paidNarrativeId = snapshot.paidNarrativeId();
                entity.paidNarrativeVersion = provenance.paidNarrativeVersion();
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
                .orElseThrow(() -> new IllegalStateException("the detailed report was not persisted"));
    }

    /** Read back exactly as stored: the report document is never re-assembled under today's code. */
    private static ProfessionalDetailedReportSnapshot toSnapshot(ProfessionalDetailedReportEntity entity) {
        try {
            return new ProfessionalDetailedReportSnapshot(entity.id, entity.assessmentId, entity.entitlementId,
                    entity.analysisId, entity.guidanceId, entity.paidNarrativeId,
                    PaidNarrativeStatus.valueOf(entity.paidNarrativeStatus), entity.reportVersion, entity.pdfVersion,
                    entity.generatedAt, JSON.treeToValue(entity.report, ProfessionalDetailedReport.class));
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            throw new IllegalStateException("stored detailed report could not be read", unreadable);
        }
    }
}
