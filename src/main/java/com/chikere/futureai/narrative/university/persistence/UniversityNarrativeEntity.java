package com.chikere.futureai.narrative.university.persistence;

import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "university_assessment_narrative")
class UniversityNarrativeEntity {
    @Id @Column(name = "assessment_id") UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) NarrativeStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) NarrativeSource source;
    @Column(name = "provider_identifier", nullable = false) String providerIdentifier;
    @Column(name = "model_identifier") String modelIdentifier;
    @Column(name = "prompt_schema_version", nullable = false) String promptSchemaVersion;
    @Column(name = "attempted_at", nullable = false) Instant attemptedAt;
    @Column(name = "generated_at") Instant generatedAt;
    @Column(columnDefinition = "text") String headline;
    @Column(columnDefinition = "text") String summary;
    @Column(name = "what_this_means", columnDefinition = "text") String whatThisMeans;
    @Column(name = "future_readiness_explanation", columnDefinition = "text") String futureReadinessExplanation;
    @Column(name = "strengths_explanation", columnDefinition = "text") String strengthsExplanation;
    @Column(name = "development_opportunities_explanation", columnDefinition = "text")
    String developmentOpportunitiesExplanation;
    @Column(name = "complementarity_explanation", columnDefinition = "text") String complementarityExplanation;
    @Column(name = "transferable_skills_explanation", columnDefinition = "text") String transferableSkillsExplanation;
    @Column(name = "career_adaptability_explanation", columnDefinition = "text") String careerAdaptabilityExplanation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "practical_actions", columnDefinition = "jsonb")
    List<String> practicalActions;
    @Column(name = "caution_or_limitations", columnDefinition = "text") String cautionOrLimitations;

    protected UniversityNarrativeEntity() {
    }
}
