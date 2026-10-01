package com.chikere.futureai.assessment.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UniversityAssessmentJpaRepository extends JpaRepository<UniversityAssessmentEntity, UUID> { }
