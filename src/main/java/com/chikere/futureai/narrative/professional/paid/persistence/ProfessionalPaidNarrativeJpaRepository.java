package com.chikere.futureai.narrative.professional.paid.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProfessionalPaidNarrativeJpaRepository extends JpaRepository<ProfessionalPaidNarrativeEntity, UUID> {

    Optional<ProfessionalPaidNarrativeEntity> findByAssessmentId(UUID assessmentId);
}
