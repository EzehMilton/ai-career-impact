package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "a_level_report_entitlement")
class ALevelReportEntitlementEntity {
    @Id @Column(name = "entitlement_id") UUID entitlementId;
    @Column(name = "assessment_id", nullable = false, unique = true) UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(name = "report_tier", nullable = false) ALevelReportTier reportTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false) ALevelEntitlementStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_provider", nullable = false)
    ALevelPaymentProvider paymentProvider;
    @Column(name = "provider_checkout_session_id", nullable = false, unique = true, columnDefinition = "text")
    String providerCheckoutSessionId;
    @Column(name = "checkout_url", nullable = false, columnDefinition = "text") String checkoutUrl;
    @Column(name = "price_configuration_key", nullable = false, columnDefinition = "text")
    String priceConfigurationKey;
    @Column(name = "provider_payment_reference", columnDefinition = "text") String providerPaymentReference;
    @Column(name = "purchased_report_version") String purchasedReportVersion;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;

    protected ALevelReportEntitlementEntity() { }
}
