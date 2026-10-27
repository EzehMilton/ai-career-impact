package com.chikere.futureai.responsibility.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProfessionalResponsibilityAnalysisJpaRepository
        extends JpaRepository<ProfessionalResponsibilityAnalysisEntity, UUID> {

    Optional<ProfessionalResponsibilityAnalysisEntity> findByAssessmentId(UUID assessmentId);
}
