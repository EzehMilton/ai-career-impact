package com.chikere.futureai.narrative.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ALevelNarrativeJpaRepository extends JpaRepository<ALevelNarrativeEntity, UUID> { }
