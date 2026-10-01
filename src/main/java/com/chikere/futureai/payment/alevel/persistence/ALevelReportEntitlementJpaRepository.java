package com.chikere.futureai.payment.alevel.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ALevelReportEntitlementJpaRepository extends JpaRepository<ALevelReportEntitlementEntity, UUID> {
    Optional<ALevelReportEntitlementEntity> findByAssessmentId(UUID assessmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ALevelReportEntitlementEntity e where e.entitlementId = :id")
    Optional<ALevelReportEntitlementEntity> findByIdForUpdate(@Param("id") UUID id);
}
