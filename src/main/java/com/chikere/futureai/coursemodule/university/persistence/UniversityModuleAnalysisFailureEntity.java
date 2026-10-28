package com.chikere.futureai.coursemodule.university.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "university_module_analysis_failure")
class UniversityModuleAnalysisFailureEntity {

    @Id UUID id;
    @Column(name = "assessment_id", nullable = false) UUID assessmentId;
    @Column(name = "failed_at", nullable = false) Instant failedAt;
    @Column(nullable = false) String category;
    @Column(nullable = false) boolean retryable;
    @Column(name = "error_type", nullable = false, columnDefinition = "text") String errorType;
    @Column(name = "error_message", columnDefinition = "text") String errorMessage;
    @Column(name = "schema_version", nullable = false) String schemaVersion;
    @Column(name = "policy_version", nullable = false) String policyVersion;

    protected UniversityModuleAnalysisFailureEntity() {}
}
