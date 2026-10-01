package com.chikere.futureai.payment.professional.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ProfessionalReportEntitlementJpaRepository
        extends JpaRepository<ProfessionalReportEntitlementEntity, UUID> {
    Optional<ProfessionalReportEntitlementEntity> findByAssessmentId(UUID assessmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProfessionalReportEntitlementEntity e where e.entitlementId = :id")
    Optional<ProfessionalReportEntitlementEntity> findByIdForUpdate(@Param("id") UUID id);
}
