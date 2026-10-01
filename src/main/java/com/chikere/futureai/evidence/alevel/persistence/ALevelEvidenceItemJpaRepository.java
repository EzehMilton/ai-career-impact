package com.chikere.futureai.evidence.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

interface ALevelEvidenceItemJpaRepository extends JpaRepository<ALevelEvidenceItemEntity, UUID> {
    List<ALevelEvidenceItemEntity> findByAssessmentIdOrderByItemOrder(UUID assessmentId);

    @Modifying
    void deleteByAssessmentId(UUID assessmentId);
}
