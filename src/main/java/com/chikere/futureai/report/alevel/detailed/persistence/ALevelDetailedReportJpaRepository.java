package com.chikere.futureai.report.alevel.detailed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ALevelDetailedReportJpaRepository extends JpaRepository<ALevelDetailedReportEntity, UUID> {

    Optional<ALevelDetailedReportEntity> findByAssessmentId(UUID assessmentId);
}
