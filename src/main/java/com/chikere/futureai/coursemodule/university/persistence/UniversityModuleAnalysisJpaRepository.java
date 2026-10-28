package com.chikere.futureai.coursemodule.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UniversityModuleAnalysisJpaRepository extends JpaRepository<UniversityModuleAnalysisEntity, UUID> {

    Optional<UniversityModuleAnalysisEntity> findByAssessmentId(UUID assessmentId);
}
