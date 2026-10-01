package com.chikere.futureai.payment.professional.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedProfessionalPaymentEventJpaRepository
        extends JpaRepository<ProcessedProfessionalPaymentEventEntity, ProcessedProfessionalPaymentEventId> { }
