package com.chikere.futureai.payment.university.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface UniversityReportEntitlementJpaRepository
        extends JpaRepository<UniversityReportEntitlementEntity, UUID> {
    Optional<UniversityReportEntitlementEntity> findByAssessmentId(UUID assessmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UniversityReportEntitlementEntity e where e.entitlementId = :id")
    Optional<UniversityReportEntitlementEntity> findByIdForUpdate(@Param("id") UUID id);
}
