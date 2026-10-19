package com.chikere.futureai.payment.university.persistence;

import com.chikere.futureai.payment.university.application.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUniversityPurchaseStore implements UniversityPurchaseStore {
    private final UniversityReportEntitlementJpaRepository entitlements;
    private final PurchasedUniversityReportSnapshotJpaRepository snapshots;
    private final ProcessedUniversityPaymentEventJpaRepository events;
    private final PurchasedUniversityReportSnapshotCodec snapshotCodec;

    JpaUniversityPurchaseStore(UniversityReportEntitlementJpaRepository entitlements,
            PurchasedUniversityReportSnapshotJpaRepository snapshots,
            ProcessedUniversityPaymentEventJpaRepository events,
            PurchasedUniversityReportSnapshotCodec snapshotCodec) {
        this.entitlements = entitlements;
        this.snapshots = snapshots;
        this.events = events;
        this.snapshotCodec = snapshotCodec;
    }

    @Override
    @Transactional
    public void savePending(UniversityReportEntitlement entitlement) {
        if (entitlement.status() != UniversityEntitlementStatus.PENDING) {
            throw new IllegalArgumentException("only pending entitlements can be created");
        }
        var existing = entitlements.findByAssessmentId(entitlement.assessmentId());
        if (existing.isPresent()) {
            var entity = existing.orElseThrow();
            if (entity.status != UniversityEntitlementStatus.FAILED
                    || !entity.entitlementId.equals(entitlement.entitlementId())) {
                throw new IllegalStateException("an entitlement already exists for this assessment");
            }
            entity.status = UniversityEntitlementStatus.PENDING;
            entity.paymentProvider = entitlement.paymentProvider();
            entity.providerCheckoutSessionId = entitlement.providerCheckoutSessionId();
            entity.checkoutUrl = entitlement.checkoutUrl().toString();
            entity.priceConfigurationKey = entitlement.priceConfigurationKey();
            entity.providerPaymentReference = null;
            entity.purchasedReportVersion = null;
            entity.createdAt = entitlement.createdAt();
            entity.confirmedAt = null;
            entitlements.saveAndFlush(entity);
            return;
        }
        if (entitlements.existsById(entitlement.entitlementId())) {
            throw new IllegalStateException("an entitlement already exists with this identifier");
        }
        entitlements.saveAndFlush(toEntity(entitlement));
    }

    @Override
    @Transactional
    public void replaceExpiredCheckout(UUID entitlementId, String expiredCheckoutSessionId,
            UniversityCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt) {
        var entity = entitlements.findByIdForUpdate(entitlementId)
                .orElseThrow(() -> new IllegalStateException("entitlement does not exist"));
        // A concurrent retry with the same idempotency key already stored this exact session.
        if (replacement.checkoutSessionId().equals(entity.providerCheckoutSessionId)) return;
        if (entity.status != UniversityEntitlementStatus.PENDING
                || entity.paymentProvider != replacement.provider()
                || !expiredCheckoutSessionId.equals(entity.providerCheckoutSessionId)) {
            throw new IllegalStateException("the pending checkout changed before it could be replaced");
        }
        entity.providerCheckoutSessionId = replacement.checkoutSessionId();
        entity.checkoutUrl = replacement.redirectUrl().toString();
        entity.priceConfigurationKey = priceConfigurationKey;
        entity.createdAt = replacedAt;
        entitlements.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UniversityReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId) {
        return entitlements.findByAssessmentId(assessmentId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UniversityReportEntitlement> findEntitlementById(UUID entitlementId) {
        return entitlements.findById(entitlementId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchasedUniversityReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId) {
        return snapshots.findByAssessmentId(assessmentId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEventProcessed(UniversityPaymentProvider provider, String eventId) {
        return events.existsById(new ProcessedUniversityPaymentEventId(provider, eventId));
    }

    @Override
    @Transactional
    public PurchaseConfirmationResult confirmAndFreeze(UniversityPaymentEvent event,
            PurchasedUniversityReportSnapshot snapshot) {
        var eventId = new ProcessedUniversityPaymentEventId(event.provider(), event.eventId());
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        var entitlement = entitlements.findByIdForUpdate(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException("entitlement no longer exists"));
        // A concurrent duplicate may have committed while this transaction waited for the row lock.
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        if (entitlement.status == UniversityEntitlementStatus.ACTIVE) {
            saveEvent(event);
            return PurchaseConfirmationResult.ALREADY_PROCESSED;
        }
        if (entitlement.status != UniversityEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("entitlement is not pending");
        }
        if (!entitlement.assessmentId.equals(event.assessmentId())
                || entitlement.paymentProvider != event.provider()
                || !entitlement.providerCheckoutSessionId.equals(event.checkoutSessionId())
                || !snapshot.entitlementId().equals(entitlement.entitlementId)
                || !snapshot.assessmentId().equals(entitlement.assessmentId)) {
            throw new InvalidPaymentNotificationException("payment confirmation does not match entitlement");
        }
        var snapshotEntity = new PurchasedUniversityReportSnapshotEntity();
        snapshotEntity.entitlementId = snapshot.entitlementId();
        snapshotEntity.assessmentId = snapshot.assessmentId();
        snapshotEntity.reportVersion = snapshot.reportVersion();
        snapshotEntity.frozenAt = snapshot.frozenAt();
        snapshotEntity.reportJson = snapshotCodec.write(snapshot.report());
        snapshots.save(snapshotEntity);

        entitlement.status = UniversityEntitlementStatus.ACTIVE;
        entitlement.providerPaymentReference = event.paymentReference().orElse(null);
        entitlement.purchasedReportVersion = snapshot.reportVersion();
        entitlement.confirmedAt = snapshot.frozenAt();
        entitlements.save(entitlement);
        saveEvent(event);
        snapshots.flush();
        entitlements.flush();
        events.flush();
        return PurchaseConfirmationResult.ACTIVATED;
    }

    private void saveEvent(UniversityPaymentEvent event) {
        var entity = new ProcessedUniversityPaymentEventEntity();
        entity.paymentProvider = event.provider();
        entity.providerEventId = event.eventId();
        entity.entitlementId = event.entitlementId();
        entity.processedAt = event.occurredAt();
        events.save(entity);
    }

    private UniversityReportEntitlementEntity toEntity(UniversityReportEntitlement value) {
        var entity = new UniversityReportEntitlementEntity();
        entity.entitlementId = value.entitlementId();
        entity.assessmentId = value.assessmentId();
        entity.reportTier = value.reportTier();
        entity.status = value.status();
        entity.paymentProvider = value.paymentProvider();
        entity.providerCheckoutSessionId = value.providerCheckoutSessionId();
        entity.checkoutUrl = value.checkoutUrl().toString();
        entity.priceConfigurationKey = value.priceConfigurationKey();
        entity.providerPaymentReference = value.providerPaymentReference().orElse(null);
        entity.purchasedReportVersion = value.purchasedReportVersion().orElse(null);
        entity.createdAt = value.createdAt();
        entity.confirmedAt = value.confirmedAt().orElse(null);
        return entity;
    }

    private UniversityReportEntitlement toStored(UniversityReportEntitlementEntity entity) {
        return new UniversityReportEntitlement(entity.entitlementId, entity.assessmentId, entity.reportTier,
                entity.status, entity.paymentProvider, entity.providerCheckoutSessionId,
                URI.create(entity.checkoutUrl), entity.priceConfigurationKey,
                Optional.ofNullable(entity.providerPaymentReference),
                Optional.ofNullable(entity.purchasedReportVersion), entity.createdAt,
                Optional.ofNullable(entity.confirmedAt));
    }

    private PurchasedUniversityReportSnapshot toSnapshot(PurchasedUniversityReportSnapshotEntity entity) {
        return new PurchasedUniversityReportSnapshot(entity.entitlementId, entity.assessmentId,
                entity.reportVersion, entity.frozenAt,
                snapshotCodec.read(entity.reportVersion, entity.reportJson));
    }
}
