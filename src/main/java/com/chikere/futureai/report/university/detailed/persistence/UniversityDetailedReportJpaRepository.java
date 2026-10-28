package com.chikere.futureai.report.university.detailed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UniversityDetailedReportJpaRepository extends JpaRepository<UniversityDetailedReportEntity, UUID> {

    Optional<UniversityDetailedReportEntity> findByAssessmentId(UUID assessmentId);
}
