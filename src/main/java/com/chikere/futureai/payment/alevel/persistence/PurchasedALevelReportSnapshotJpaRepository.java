package com.chikere.futureai.payment.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PurchasedALevelReportSnapshotJpaRepository
        extends JpaRepository<PurchasedALevelReportSnapshotEntity, UUID> {
    Optional<PurchasedALevelReportSnapshotEntity> findByAssessmentId(UUID assessmentId);
}
