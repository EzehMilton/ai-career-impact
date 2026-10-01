package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_professional_payment_event")
@IdClass(ProcessedProfessionalPaymentEventId.class)
class ProcessedProfessionalPaymentEventEntity {
    @Id @Enumerated(EnumType.STRING) @Column(name = "payment_provider")
    ProfessionalPaymentProvider paymentProvider;
    @Id @Column(name = "provider_event_id", columnDefinition = "text") String providerEventId;
    @Column(name = "entitlement_id", nullable = false) UUID entitlementId;
    @Column(name = "processed_at", nullable = false) Instant processedAt;

    protected ProcessedProfessionalPaymentEventEntity() { }
}
