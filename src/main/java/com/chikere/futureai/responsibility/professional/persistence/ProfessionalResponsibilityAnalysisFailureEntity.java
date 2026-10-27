package com.chikere.futureai.responsibility.professional.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_responsibility_analysis_failure")
class ProfessionalResponsibilityAnalysisFailureEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false) UUID assessmentId;
    @Column(name = "failed_at", nullable = false) Instant failedAt;
    @Column(nullable = false) String stage;
    @Column(name = "error_type", nullable = false, columnDefinition = "text") String errorType;
    @Column(name = "error_message", columnDefinition = "text") String errorMessage;
    @Column(name = "schema_version", nullable = false) String schemaVersion;
    @Column(name = "policy_version", nullable = false) String policyVersion;

    protected ProfessionalResponsibilityAnalysisFailureEntity() {}
}
