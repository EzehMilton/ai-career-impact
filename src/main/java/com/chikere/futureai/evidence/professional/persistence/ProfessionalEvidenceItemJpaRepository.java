package com.chikere.futureai.evidence.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProfessionalEvidenceItemJpaRepository extends JpaRepository<ProfessionalEvidenceItemEntity, UUID> {
    List<ProfessionalEvidenceItemEntity> findByAssessmentIdOrderByItemOrder(UUID assessmentId);
    void deleteByAssessmentId(UUID assessmentId);
}
