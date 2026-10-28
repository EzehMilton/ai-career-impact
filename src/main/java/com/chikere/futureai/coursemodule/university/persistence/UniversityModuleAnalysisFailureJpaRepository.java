package com.chikere.futureai.coursemodule.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface UniversityModuleAnalysisFailureJpaRepository extends JpaRepository<UniversityModuleAnalysisFailureEntity, UUID> {

    List<UniversityModuleAnalysisFailureEntity> findByAssessmentIdOrderByFailedAtAsc(UUID assessmentId);
}
