package com.chikere.futureai.evidence.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

interface UniversityEvidenceItemJpaRepository extends JpaRepository<UniversityEvidenceItemEntity, UUID> {
    List<UniversityEvidenceItemEntity> findByAssessmentIdOrderByItemOrder(UUID assessmentId);

    @Modifying
    void deleteByAssessmentId(UUID assessmentId);
}
