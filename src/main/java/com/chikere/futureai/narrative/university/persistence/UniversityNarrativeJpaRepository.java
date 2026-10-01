package com.chikere.futureai.narrative.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UniversityNarrativeJpaRepository extends JpaRepository<UniversityNarrativeEntity, UUID> { }
