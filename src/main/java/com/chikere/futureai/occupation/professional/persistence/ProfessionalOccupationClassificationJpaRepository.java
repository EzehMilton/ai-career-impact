package com.chikere.futureai.occupation.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfessionalOccupationClassificationJpaRepository
        extends JpaRepository<ProfessionalOccupationClassificationEntity, UUID> { }
