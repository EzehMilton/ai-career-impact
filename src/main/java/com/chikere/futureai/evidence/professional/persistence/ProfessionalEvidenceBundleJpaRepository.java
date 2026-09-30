package com.chikere.futureai.evidence.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfessionalEvidenceBundleJpaRepository extends JpaRepository<ProfessionalEvidenceBundleEntity, UUID> { }
