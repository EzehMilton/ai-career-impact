package com.chikere.futureai.evidence.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UniversityEvidenceBundleJpaRepository extends JpaRepository<UniversityEvidenceBundleEntity, UUID> { }
