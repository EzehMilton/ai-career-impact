package com.chikere.futureai.assessment.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface ProfessionalAssessmentJpaRepository extends JpaRepository<ProfessionalAssessmentEntity, UUID> {}
