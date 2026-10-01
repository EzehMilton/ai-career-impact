package com.chikere.futureai.assessment.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ALevelAssessmentJpaRepository extends JpaRepository<ALevelAssessmentEntity, UUID> { }
