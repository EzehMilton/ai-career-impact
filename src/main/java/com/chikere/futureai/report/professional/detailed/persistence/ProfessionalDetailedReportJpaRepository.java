package com.chikere.futureai.report.professional.detailed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProfessionalDetailedReportJpaRepository extends JpaRepository<ProfessionalDetailedReportEntity, UUID> {

    Optional<ProfessionalDetailedReportEntity> findByAssessmentId(UUID assessmentId);
}
