package com.chikere.futureai.narrative.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfessionalNarrativeJpaRepository extends JpaRepository<ProfessionalNarrativeEntity, UUID> {
}
