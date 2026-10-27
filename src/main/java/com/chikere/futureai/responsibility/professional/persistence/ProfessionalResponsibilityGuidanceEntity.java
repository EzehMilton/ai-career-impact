package com.chikere.futureai.responsibility.professional.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** JPA storage shape of an immutable guidance snapshot; inserted once, never updated. */
@Entity
@Table(name = "professional_responsibility_guidance")
class ProfessionalResponsibilityGuidanceEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(name = "analysis_id", nullable = false, updatable = false) UUID analysisId;
    @Column(name = "policy_version", nullable = false) String policyVersion;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") JsonNode guidance;

    protected ProfessionalResponsibilityGuidanceEntity() {}
}
