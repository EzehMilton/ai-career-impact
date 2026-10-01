package com.chikere.futureai.payment.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PurchasedUniversityReportSnapshotJpaRepository
        extends JpaRepository<PurchasedUniversityReportSnapshotEntity, UUID> {
    Optional<PurchasedUniversityReportSnapshotEntity> findByAssessmentId(UUID assessmentId);
}
