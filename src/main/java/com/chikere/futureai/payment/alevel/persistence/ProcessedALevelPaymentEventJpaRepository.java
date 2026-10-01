package com.chikere.futureai.payment.alevel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedALevelPaymentEventJpaRepository
        extends JpaRepository<ProcessedALevelPaymentEventEntity, ProcessedALevelPaymentEventId> { }
