package com.chikere.futureai.report.professional.detailed.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** JPA storage shape of an immutable assembled detailed report; inserted once, never updated. */
@Entity
@Table(name = "professional_detailed_report")
class ProfessionalDetailedReportEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(name = "entitlement_id", nullable = false, updatable = false) UUID entitlementId;
    @Column(name = "role_report_version", nullable = false) String roleReportVersion;
    @Column(name = "decision_policy_version", nullable = false) String decisionPolicyVersion;
    @Column(name = "analysis_id", nullable = false) UUID analysisId;
    @Column(name = "responsibility_schema_version", nullable = false) String responsibilitySchemaVersion;
    @Column(name = "responsibility_policy_version", nullable = false) String responsibilityPolicyVersion;
    @Column(name = "guidance_id", nullable = false) UUID guidanceId;
    @Column(name = "guidance_policy_version", nullable = false) String guidancePolicyVersion;
    @Column(name = "paid_narrative_id", nullable = false) UUID paidNarrativeId;
    @Column(name = "paid_narrative_version", nullable = false) String paidNarrativeVersion;
    @Column(name = "paid_narrative_status", nullable = false) String paidNarrativeStatus;
    @Column(name = "report_version", nullable = false) String reportVersion;
    @Column(name = "pdf_version", nullable = false) String pdfVersion;
    @Column(name = "generated_at", nullable = false) Instant generatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") JsonNode report;

    protected ProfessionalDetailedReportEntity() {}
}
