package com.chikere.futureai.assessment.university.persistence;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.decision.university.ProfileBand;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityFactorDirection;
import com.chikere.futureai.decision.university.UniversityFactorEffect;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JPA storage shape, deliberately separate from the university domain and decision records. */
@Entity
@Table(name = "university_assessment")
class UniversityAssessmentEntity {
    @Id UUID id;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "course_title", nullable = false, columnDefinition = "text") String courseTitle;
    @Column(name = "course_description", nullable = false, columnDefinition = "text") String courseDescription;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "key_modules", nullable = false, columnDefinition = "jsonb")
    List<String> keyModules;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "skills_developed", nullable = false, columnDefinition = "jsonb")
    List<String> skillsDeveloped;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "preferred_career_areas", nullable = false, columnDefinition = "jsonb")
    List<String> preferredCareerAreas;
    @Column(columnDefinition = "text") String institution;
    @Column(columnDefinition = "text") String country;
    @Enumerated(EnumType.STRING) @Column(nullable = false) AssessmentSource source;
    @Column(name = "model_identifier", columnDefinition = "text") String modelIdentifier;
    @Column(name = "judgment_schema_version", nullable = false) String judgmentSchemaVersion;
    @Column(name = "decision_policy_version", nullable = false) String decisionPolicyVersion;
    @Enumerated(EnumType.STRING) @Column(name = "judgment_journey", nullable = false) JourneyType judgmentJourney;
    @Column(name = "information_sufficiency", nullable = false) double informationSufficiency;
    @Enumerated(EnumType.STRING) @Column(name = "decision_journey", nullable = false) JourneyType decisionJourney;
    @Column(name = "overall_indicator") Double overallIndicator;
    @Column(name = "result_label", nullable = false, columnDefinition = "text") String resultLabel;
    @Enumerated(EnumType.STRING) @Column(name = "assessment_confidence", nullable = false)
    AssessmentConfidence assessmentConfidence;
    @Enumerated(EnumType.STRING) @Column(name = "assessment_status", nullable = false)
    AssessmentStatus assessmentStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "key_strengths", nullable = false, columnDefinition = "jsonb")
    List<String> keyStrengths;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "development_opportunities", nullable = false,
            columnDefinition = "jsonb") List<String> developmentOpportunities;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_assessment_judgment", joinColumns = @JoinColumn(name = "assessment_id"))
    @OrderColumn(name = "position")
    List<JudgmentRow> judgments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_assessment_measure", joinColumns = @JoinColumn(name = "assessment_id"))
    List<MeasureRow> measures = new ArrayList<>();

    /** The three university-decision-v2 measures; empty for a historical v1 assessment. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_assessment_profile_measure", joinColumns = @JoinColumn(name = "assessment_id"))
    List<ProfileMeasureRow> profileMeasures = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_assessment_profile_factor", joinColumns = @JoinColumn(name = "assessment_id"))
    List<ProfileFactorRow> profileFactors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "university_assessment_factor", joinColumns = @JoinColumn(name = "assessment_id"))
    List<FactorRow> factors = new ArrayList<>();

    protected UniversityAssessmentEntity() {
    }

    @Embeddable
    static class JudgmentRow {
        @Column(name = "dimension_id", nullable = false) String dimensionId;
        @Column(name = "dimension_name", nullable = false, columnDefinition = "text") String dimensionName;
        @Column(name = "numeric_value") Double numericValue;
        @Column(name = "categorical_value", columnDefinition = "text") String categoricalValue;
        @Enumerated(EnumType.STRING) AssessmentConfidence confidence;
        @Column(name = "confidence_value") Double confidenceValue;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "probability_distribution", nullable = false, columnDefinition = "jsonb")
        Map<String, Double> probabilityDistribution;
        protected JudgmentRow() { }
    }

    @Embeddable
    static class MeasureRow {
        @Enumerated(EnumType.STRING) @Column(nullable = false) UniversityMeasure measure;
        @Column(nullable = false) double indicator;
        @Enumerated(EnumType.STRING) @Column(nullable = false) ReadinessBand band;
        @Column(nullable = false) double confidence;
        protected MeasureRow() { }
    }

    @Embeddable
    static class FactorRow {
        @Column(name = "factor_type", nullable = false) String factorType;
        @Column(name = "position", nullable = false) int position;
        @Column(name = "dimension_id", nullable = false) String dimensionId;
        @Enumerated(EnumType.STRING) @Column(nullable = false) UniversityFactorEffect effect;
        @Enumerated(EnumType.STRING) @Column(nullable = false) UniversityFactorDirection direction;
        @Column(nullable = false) double score;
        @Column(name = "readiness_component", nullable = false) double readinessComponent;
        @Column(nullable = false) double weight;
        @Column(name = "signed_effect", nullable = false) double signedEffect;
        protected FactorRow() { }
    }

    @Embeddable
    static class ProfileMeasureRow {
        @Enumerated(EnumType.STRING) @Column(nullable = false) UniversityProfileMeasure measure;
        @Column(nullable = false) double indicator;
        @Enumerated(EnumType.STRING) @Column(nullable = false) ProfileBand band;
        @Column(nullable = false) double confidence;
        @Column(name = "lower_confidence", nullable = false) boolean lowerConfidence;
        protected ProfileMeasureRow() { }
    }

    @Embeddable
    static class ProfileFactorRow {
        @Enumerated(EnumType.STRING) @Column(nullable = false) UniversityProfileMeasure measure;
        @Column(nullable = false) int position;
        @Column(name = "dimension_id", nullable = false) String dimensionId;
        @Column(nullable = false) double score;
        @Column(nullable = false) double weight;
        @Column(nullable = false) double effect;
        @Column(name = "driver_position") Integer driverPosition;
        @Column(name = "limiting_position") Integer limitingPosition;
        protected ProfileFactorRow() { }
    }
}
