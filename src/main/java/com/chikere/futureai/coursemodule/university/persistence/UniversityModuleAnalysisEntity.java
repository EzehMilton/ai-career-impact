package com.chikere.futureai.coursemodule.university.persistence;

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

/** JPA storage shape of a completed University module-analysis snapshot; inserted once, never updated. */
@Entity
@Table(name = "university_module_analysis")
class UniversityModuleAnalysisEntity {

    @Id UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) UUID assessmentId;
    @Column(nullable = false, updatable = false) String status;
    @Column(name = "schema_version", nullable = false, updatable = false) String schemaVersion;
    @Column(name = "policy_version", nullable = false, updatable = false) String policyVersion;
    @Column(name = "judgment_source", nullable = false, updatable = false) String judgmentSource;
    @Column(columnDefinition = "text", updatable = false) String model;
    @Column(name = "provider_request_id", columnDefinition = "text", updatable = false) String providerRequestId;
    @Column(name = "input_tokens", updatable = false) Long inputTokens;
    @Column(name = "output_tokens", updatable = false) Long outputTokens;
    @Column(name = "course_title", nullable = false, columnDefinition = "text", updatable = false) String courseTitle;
    @Column(name = "course_description", nullable = false, columnDefinition = "text", updatable = false)
    String courseDescription;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "skills_developed", nullable = false, columnDefinition = "jsonb",
            updatable = false)
    List<String> skillsDeveloped;
    @Column(name = "selected_module_count", nullable = false, updatable = false) int selectedModuleCount;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "completed_at", nullable = false, updatable = false) Instant completedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_module_analysis_item", joinColumns = @JoinColumn(name = "analysis_id"))
    List<ItemRow> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_module_analysis_judgment", joinColumns = @JoinColumn(name = "analysis_id"))
    List<JudgmentRow> judgments = new ArrayList<>();

    protected UniversityModuleAnalysisEntity() {}

    @Embeddable
    static class ItemRow {
        @Column(name = "request_index", nullable = false) int requestIndex;
        @Column(name = "module_key", nullable = false) String moduleKey;
        @Column(name = "module_id", nullable = false) int moduleId;
        @Column(name = "module_title", nullable = false, columnDefinition = "text") String moduleTitle;
        @Column(nullable = false) String category;
        @Column(name = "display_label", nullable = false, columnDefinition = "text") String displayLabel;
        @Column(name = "classification_rule", nullable = false) String classificationRule;
        @Column(name = "dominant_value", nullable = false) double dominantValue;
        @Column(name = "competing_value", nullable = false) double competingValue;
        @Column(name = "dominance_gap", nullable = false) double dominanceGap;
        @Column(name = "independent_demonstration_sensitive", nullable = false) boolean independentDemonstrationSensitive;
        @Column(name = "professional_standards_relevant", nullable = false) boolean professionalStandardsRelevant;
        @Column(name = "close_call", nullable = false) boolean closeCall;
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "close_call_reasons", nullable = false, columnDefinition = "jsonb")
        List<String> closeCallReasons;

        protected ItemRow() {}
    }

    @Embeddable
    static class JudgmentRow {
        @Column(name = "request_index", nullable = false) int requestIndex;
        @Column(nullable = false) String judgment;
        @Column(name = "judgment_value", nullable = false) double value;
        @Column(name = "provider_confidence") Double providerConfidence;
        @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") Map<String, Double> distribution;
        @Column(name = "nearest_level") Integer nearestLevel;
        @Column(name = "nearest_label", columnDefinition = "text") String nearestLabel;

        protected JudgmentRow() {}
    }
}
