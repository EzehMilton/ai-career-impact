package com.chikere.futureai.evidence.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ALevelEvidenceBundleJpaRepository extends JpaRepository<ALevelEvidenceBundleEntity, UUID> { }
