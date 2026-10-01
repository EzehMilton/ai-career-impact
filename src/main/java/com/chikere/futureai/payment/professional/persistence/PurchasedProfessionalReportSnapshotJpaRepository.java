package com.chikere.futureai.payment.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PurchasedProfessionalReportSnapshotJpaRepository
        extends JpaRepository<PurchasedProfessionalReportSnapshotEntity, UUID> {
    Optional<PurchasedProfessionalReportSnapshotEntity> findByAssessmentId(UUID assessmentId);
}
