package com.chikere.futureai.assessment.professional.persistence;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalInputSource;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
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

/** JPA storage shape, deliberately separate from assessment and decision records. */
@Entity
@Table(name = "professional_assessment")
class ProfessionalAssessmentEntity {
    @Id UUID id;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(nullable = false, columnDefinition = "text") String profession;
    @Column(name = "work_description", nullable = false, columnDefinition = "text") String workDescription;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") List<String> responsibilities;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") List<String> skills;
    @Column(name = "years_of_experience") Integer yearsOfExperience;
    @Enumerated(EnumType.STRING) @Column(name = "input_source", nullable = false) ProfessionalInputSource inputSource;
    @Column(name = "cv_original_filename", columnDefinition = "text") String cvOriginalFilename;
    @Column(name = "cv_media_type", columnDefinition = "text") String cvMediaType;
    @Column(name = "cv_parser_version") String cvParserVersion;
    @Column(name = "cv_parsed_at") Instant cvParsedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) AssessmentSource source;
    @Column(name = "model_identifier", columnDefinition = "text") String modelIdentifier;
    @Column(name = "judgment_schema_version", nullable = false) String judgmentSchemaVersion;
    @Column(name = "decision_policy_version", nullable = false) String decisionPolicyVersion;
    @Enumerated(EnumType.STRING) @Column(name = "judgment_journey", nullable = false) JourneyType judgmentJourney;
    @Column(name = "information_sufficiency", nullable = false) double informationSufficiency;
    @Enumerated(EnumType.STRING) @Column(name = "decision_journey", nullable = false) JourneyType decisionJourney;
    @Column(name = "overall_indicator") Double overallIndicator;
    @Column(name = "result_label", nullable = false, columnDefinition = "text") String resultLabel;
    @Enumerated(EnumType.STRING) @Column(name = "assessment_confidence", nullable = false) AssessmentConfidence assessmentConfidence;
    @Enumerated(EnumType.STRING) @Column(name = "assessment_status", nullable = false) AssessmentStatus assessmentStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "key_drivers", nullable = false, columnDefinition = "jsonb") List<String> keyDrivers;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "decision_protective_factors", nullable = false, columnDefinition = "jsonb") List<String> decisionProtectiveFactors;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professional_assessment_judgment", joinColumns = @JoinColumn(name = "assessment_id"))
    @OrderColumn(name = "position")
    List<JudgmentRow> judgments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professional_assessment_measure", joinColumns = @JoinColumn(name = "assessment_id"))
    List<MeasureRow> measures = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "professional_assessment_factor", joinColumns = @JoinColumn(name = "assessment_id"))
    List<FactorRow> factors = new ArrayList<>();

    protected ProfessionalAssessmentEntity() {}

    @Embeddable
    static class JudgmentRow {
        @Column(name = "dimension_id", nullable = false) String dimensionId;
        @Column(name = "dimension_name", nullable = false, columnDefinition = "text") String dimensionName;
        @Column(name = "numeric_value") Double numericValue;
        @Column(name = "categorical_value", columnDefinition = "text") String categoricalValue;
        @Enumerated(EnumType.STRING) AssessmentConfidence confidence;
        @Column(name = "confidence_value") Double confidenceValue;
        @JdbcTypeCode(SqlTypes.JSON) @Column(name = "probability_distribution", nullable = false, columnDefinition = "jsonb")
        Map<String, Double> probabilityDistribution;
        protected JudgmentRow() {}
    }

    @Embeddable
    static class MeasureRow {
        @Enumerated(EnumType.STRING) @Column(nullable = false) ProfessionalMeasure measure;
        @Column(nullable = false) double indicator;
        @Enumerated(EnumType.STRING) @Column(nullable = false) IndicatorBand band;
        @Column(nullable = false) double confidence;
        protected MeasureRow() {}
    }

    @Embeddable
    static class FactorRow {
        @Column(name = "factor_type", nullable = false) String factorType;
        @Column(name = "position", nullable = false) int position;
        @Column(name = "dimension_id", nullable = false) String dimensionId;
        @Enumerated(EnumType.STRING) @Column(nullable = false) DecisionFactorEffect effect;
        @Enumerated(EnumType.STRING) @Column(nullable = false) ProfessionalFactorDirection direction;
        @Column(nullable = false) double score;
        @Column(name = "exposure_component", nullable = false) double exposureComponent;
        @Column(nullable = false) double weight;
        @Column(name = "signed_effect", nullable = false) double signedEffect;
        protected FactorRow() {}
    }
}
