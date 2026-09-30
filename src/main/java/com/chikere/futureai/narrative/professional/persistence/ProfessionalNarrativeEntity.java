package com.chikere.futureai.narrative.professional.persistence;

import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
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
@Table(name = "professional_assessment_narrative")
class ProfessionalNarrativeEntity {
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
    @Column(name = "exposure_explanation", columnDefinition = "text") String exposureExplanation;
    @Column(name = "human_advantage_explanation", columnDefinition = "text") String humanAdvantageExplanation;
    @Column(name = "complementarity_explanation", columnDefinition = "text") String complementarityExplanation;
    @Column(name = "adaptability_explanation", columnDefinition = "text") String adaptabilityExplanation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "practical_actions", columnDefinition = "jsonb")
    List<String> practicalActions;
    @Column(name = "caution_or_limitations", columnDefinition = "text") String cautionOrLimitations;

    protected ProfessionalNarrativeEntity() {
    }
}
