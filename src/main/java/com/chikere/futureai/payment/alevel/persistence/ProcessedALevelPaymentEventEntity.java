package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_a_level_payment_event")
@IdClass(ProcessedALevelPaymentEventId.class)
class ProcessedALevelPaymentEventEntity {
    @Id @Enumerated(EnumType.STRING) @Column(name = "payment_provider") ALevelPaymentProvider paymentProvider;
    @Id @Column(name = "provider_event_id", columnDefinition = "text") String providerEventId;
    @Column(name = "entitlement_id", nullable = false) UUID entitlementId;
    @Column(name = "processed_at", nullable = false) Instant processedAt;

    protected ProcessedALevelPaymentEventEntity() { }
}
