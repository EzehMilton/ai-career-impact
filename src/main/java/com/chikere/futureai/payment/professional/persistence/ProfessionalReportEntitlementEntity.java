package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_report_entitlement")
class ProfessionalReportEntitlementEntity {
    @Id @Column(name = "entitlement_id") UUID entitlementId;
    @Column(name = "assessment_id", nullable = false, unique = true) UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(name = "report_tier", nullable = false) ProfessionalReportTier reportTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false) ProfessionalEntitlementStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_provider", nullable = false)
    ProfessionalPaymentProvider paymentProvider;
    @Column(name = "provider_checkout_session_id", nullable = false, unique = true, columnDefinition = "text")
    String providerCheckoutSessionId;
    @Column(name = "checkout_url", nullable = false, columnDefinition = "text") String checkoutUrl;
    @Column(name = "price_configuration_key", nullable = false, columnDefinition = "text") String priceConfigurationKey;
    @Column(name = "provider_payment_reference", columnDefinition = "text") String providerPaymentReference;
    @Column(name = "purchased_report_version") String purchasedReportVersion;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;

    protected ProfessionalReportEntitlementEntity() { }
}
