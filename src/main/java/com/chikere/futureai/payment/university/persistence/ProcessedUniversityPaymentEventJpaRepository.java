package com.chikere.futureai.payment.university.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedUniversityPaymentEventJpaRepository
        extends JpaRepository<ProcessedUniversityPaymentEventEntity, ProcessedUniversityPaymentEventId> { }
