package com.chikere.futureai.evidence.university.persistence;

import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "university_assessment_evidence_bundle")
class UniversityEvidenceBundleEntity {
    @Id @Column(name = "assessment_id") UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) EvidenceStatus status;
    @Column(name = "evidence_version", nullable = false) String evidenceVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) EvidenceSource source;
    @Column(name = "provider_identifier", nullable = false) String providerIdentifier;
    @Column(name = "retrieved_at", nullable = false) Instant retrievedAt;

    protected UniversityEvidenceBundleEntity() { }
}
