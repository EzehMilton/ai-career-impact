package com.chikere.futureai.narrative.professional.paid.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** JPA storage shape of one immutable paid-narrative outcome; inserted once, never updated. */
@Entity
@Table(name = "professional_paid_narrative")
class ProfessionalPaidNarrativeEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(name = "analysis_id", nullable = false, updatable = false) UUID analysisId;
    @Column(name = "guidance_id", nullable = false, updatable = false) UUID guidanceId;
    @Column(name = "narrative_version", nullable = false) String narrativeVersion;
    @Column(nullable = false) String status;
    @Column(nullable = false) String provider;
    String model;
    @Column(name = "provider_request_id") String providerRequestId;
    @Column(nullable = false) int attempts;
    @Column(name = "fallback_reason") String fallbackReason;
    @Column(name = "generated_at", nullable = false) Instant generatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") JsonNode narrative;

    protected ProfessionalPaidNarrativeEntity() {}
}
