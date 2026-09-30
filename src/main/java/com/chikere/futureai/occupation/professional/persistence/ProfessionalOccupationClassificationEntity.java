package com.chikere.futureai.occupation.professional.persistence;

import com.chikere.futureai.occupation.professional.application.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_occupation_classification")
class ProfessionalOccupationClassificationEntity {
    @Id @Column(name = "assessment_id") UUID assessmentId;
    @Column(name = "canonical_occupation", nullable = false, columnDefinition = "text") String canonicalOccupation;
    @Enumerated(EnumType.STRING) @Column(name = "classification_system", nullable = false)
    OccupationClassificationSystem classificationSystem;
    @Column(name = "classification_code", nullable = false) String classificationCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) OccupationClassificationSource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false) OccupationClassificationQuality quality;
    @Column(name = "classifier_identifier", nullable = false) String classifierIdentifier;
    @Column(name = "classifier_version", nullable = false) String classifierVersion;
    @Column(name = "classified_at", nullable = false) Instant classifiedAt;

    protected ProfessionalOccupationClassificationEntity() { }
}
