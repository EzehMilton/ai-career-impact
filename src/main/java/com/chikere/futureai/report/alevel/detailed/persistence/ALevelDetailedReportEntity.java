package com.chikere.futureai.report.alevel.detailed.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** JPA storage shape of an immutable a-level-report-v2; inserted once, never updated. */
@Entity
@Table(name = "a_level_detailed_report")
class ALevelDetailedReportEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(name = "entitlement_id", nullable = false, updatable = false) UUID entitlementId;
    @Column(name = "judgment_schema_version", nullable = false, updatable = false) String judgmentSchemaVersion;
    @Column(name = "decision_policy_version", nullable = false, updatable = false) String decisionPolicyVersion;
    @Column(name = "guidance_policy_version", nullable = false, updatable = false) String guidancePolicyVersion;
    @Column(name = "paid_narrative_version", nullable = false, updatable = false) String paidNarrativeVersion;
    @Column(name = "paid_narrative_status", nullable = false, updatable = false) String paidNarrativeStatus;
    @Column(name = "evidence_version", updatable = false) String evidenceVersion;
    @Column(name = "report_version", nullable = false, updatable = false) String reportVersion;
    @Column(name = "pdf_version", nullable = false, updatable = false) String pdfVersion;
    @Column(name = "generated_at", nullable = false, updatable = false) Instant generatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb") JsonNode report;

    protected ALevelDetailedReportEntity() {}
}
