package com.chikere.futureai.payment.university.persistence;

import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_university_payment_event")
@IdClass(ProcessedUniversityPaymentEventId.class)
class ProcessedUniversityPaymentEventEntity {
    @Id @Enumerated(EnumType.STRING) @Column(name = "payment_provider")
    UniversityPaymentProvider paymentProvider;
    @Id @Column(name = "provider_event_id", columnDefinition = "text") String providerEventId;
    @Column(name = "entitlement_id", nullable = false) UUID entitlementId;
    @Column(name = "processed_at", nullable = false) Instant processedAt;

    protected ProcessedUniversityPaymentEventEntity() { }
}
