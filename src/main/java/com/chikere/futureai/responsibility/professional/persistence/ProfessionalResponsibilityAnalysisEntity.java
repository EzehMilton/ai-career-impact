package com.chikere.futureai.responsibility.professional.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA storage shape of a completed responsibility-analysis snapshot; inserted once, never updated. */
@Entity
@Table(name = "professional_responsibility_analysis")
class ProfessionalResponsibilityAnalysisEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(nullable = false) String status;
    @Column(name = "schema_version", nullable = false) String schemaVersion;
    @Column(name = "policy_version", nullable = false) String policyVersion;
    @Column(name = "judgment_source", nullable = false) String judgmentSource;
    @Column(columnDefinition = "text") String model;
    @Column(name = "provider_request_id", columnDefinition = "text") String providerRequestId;
    @Column(name = "input_tokens") Long inputTokens;
    @Column(name = "output_tokens") Long outputTokens;
    @Column(nullable = false, columnDefinition = "text") String profession;
    @Column(name = "work_description", nullable = false, columnDefinition = "text") String workDescription;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") List<String> skills;
    @Column(name = "regulated_context_value", nullable = false) double regulatedContextValue;
    @Column(name = "regulated_context_level", nullable = false) String regulatedContextLevel;
    @Column(name = "regulated_context_confidence") Double regulatedContextConfidence;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "regulated_context_distribution", nullable = false, columnDefinition = "jsonb")
    Map<String, Double> regulatedContextDistribution;
    @Column(name = "regulated_context_nearest_level") Integer regulatedContextNearestLevel;
    @Column(name = "regulated_context_nearest_label", columnDefinition = "text") String regulatedContextNearestLabel;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "policy_parameters", nullable = false, columnDefinition = "jsonb")
    Map<String, Double> policyParameters;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "completed_at", nullable = false) Instant completedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professional_responsibility_analysis_item", joinColumns = @JoinColumn(name = "analysis_id"))
    List<ItemRow> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professional_responsibility_analysis_judgment", joinColumns = @JoinColumn(name = "analysis_id"))
    List<JudgmentRow> judgments = new ArrayList<>();

    protected ProfessionalResponsibilityAnalysisEntity() {}

    @Embeddable
    static class ItemRow {
        @Column(name = "responsibility_index", nullable = false) int responsibilityIndex;
        @Column(name = "responsibility_key", nullable = false) String responsibilityKey;
        @Column(name = "responsibility_text", nullable = false, columnDefinition = "text") String responsibilityText;
        @Column(name = "described_clearly", nullable = false) double describedClearly;
        @Column(nullable = false) String category;
        @Column(name = "display_label", nullable = false, columnDefinition = "text") String displayLabel;
        @Column(name = "needs_close_human_oversight", nullable = false) boolean needsCloseHumanOversight;
        @Column(name = "oversight_rule") String oversightRule;
        @Column(name = "classification_rule", nullable = false) String classificationRule;
        @Column(name = "close_call", nullable = false) boolean closeCall;
        protected ItemRow() {}
    }

    @Embeddable
    static class JudgmentRow {
        @Column(name = "responsibility_index", nullable = false) int responsibilityIndex;
        @Column(nullable = false) String judgment;
        @Column(name = "judgment_value", nullable = false) double value;
        @Column(name = "judgment_level", nullable = false) String level;
        @Column(name = "provider_confidence") Double providerConfidence;
        @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") Map<String, Double> distribution;
        @Column(name = "nearest_level") Integer nearestLevel;
        @Column(name = "nearest_label", columnDefinition = "text") String nearestLabel;
        protected JudgmentRow() {}
    }
}
