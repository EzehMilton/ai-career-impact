package com.chikere.futureai.responsibility.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProfessionalResponsibilityAnalysisFailureJpaRepository
        extends JpaRepository<ProfessionalResponsibilityAnalysisFailureEntity, UUID> {

    List<ProfessionalResponsibilityAnalysisFailureEntity> findByAssessmentIdOrderByFailedAtAsc(UUID assessmentId);
}
