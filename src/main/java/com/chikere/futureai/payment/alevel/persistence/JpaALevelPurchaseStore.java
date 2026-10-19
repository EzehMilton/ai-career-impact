package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.payment.alevel.application.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaALevelPurchaseStore implements ALevelPurchaseStore {
    private final ALevelReportEntitlementJpaRepository entitlements;
    private final PurchasedALevelReportSnapshotJpaRepository snapshots;
    private final ProcessedALevelPaymentEventJpaRepository events;
    private final PurchasedALevelReportSnapshotCodec snapshotCodec;

    JpaALevelPurchaseStore(ALevelReportEntitlementJpaRepository entitlements,
            PurchasedALevelReportSnapshotJpaRepository snapshots,
            ProcessedALevelPaymentEventJpaRepository events,
            PurchasedALevelReportSnapshotCodec snapshotCodec) {
        this.entitlements = entitlements;
        this.snapshots = snapshots;
        this.events = events;
        this.snapshotCodec = snapshotCodec;
    }

    @Override
    @Transactional
    public void savePending(ALevelReportEntitlement entitlement) {
        if (entitlement.status() != ALevelEntitlementStatus.PENDING) {
            throw new IllegalArgumentException("only pending entitlements can be created");
        }
        var existing = entitlements.findByAssessmentId(entitlement.assessmentId());
        if (existing.isPresent()) {
            var entity = existing.orElseThrow();
            if (entity.status != ALevelEntitlementStatus.FAILED
                    || !entity.entitlementId.equals(entitlement.entitlementId())) {
                throw new IllegalStateException("an entitlement already exists for this assessment");
            }
            entity.status = ALevelEntitlementStatus.PENDING;
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
            ALevelCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt) {
        var entity = entitlements.findByIdForUpdate(entitlementId)
                .orElseThrow(() -> new IllegalStateException("entitlement does not exist"));
        // A concurrent retry with the same idempotency key already stored this exact session.
        if (replacement.checkoutSessionId().equals(entity.providerCheckoutSessionId)) return;
        if (entity.status != ALevelEntitlementStatus.PENDING
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
    public Optional<ALevelReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId) {
        return entitlements.findByAssessmentId(assessmentId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ALevelReportEntitlement> findEntitlementById(UUID entitlementId) {
        return entitlements.findById(entitlementId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchasedALevelReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId) {
        return snapshots.findByAssessmentId(assessmentId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEventProcessed(ALevelPaymentProvider provider, String eventId) {
        return events.existsById(new ProcessedALevelPaymentEventId(provider, eventId));
    }

    @Override
    @Transactional
    public PurchaseConfirmationResult confirmAndFreeze(ALevelPaymentEvent event,
            PurchasedALevelReportSnapshot snapshot) {
        var eventId = new ProcessedALevelPaymentEventId(event.provider(), event.eventId());
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        var entitlement = entitlements.findByIdForUpdate(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException("entitlement no longer exists"));
        // A concurrent duplicate may have committed while this transaction waited for the row lock.
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        if (entitlement.status == ALevelEntitlementStatus.ACTIVE) {
            saveEvent(event);
            return PurchaseConfirmationResult.ALREADY_PROCESSED;
        }
        if (entitlement.status != ALevelEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("entitlement is not pending");
        }
        if (!entitlement.assessmentId.equals(event.assessmentId())
                || entitlement.paymentProvider != event.provider()
                || !entitlement.providerCheckoutSessionId.equals(event.checkoutSessionId())
                || !snapshot.entitlementId().equals(entitlement.entitlementId)
                || !snapshot.assessmentId().equals(entitlement.assessmentId)) {
            throw new InvalidPaymentNotificationException("payment confirmation does not match entitlement");
        }
        snapshots.save(toSnapshotEntity(snapshot));

        entitlement.status = ALevelEntitlementStatus.ACTIVE;
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

    @Override
    @Transactional
    public void saveComplimentary(ALevelReportEntitlement entitlement, PurchasedALevelReportSnapshot snapshot) {
        if (entitlement.status() != ALevelEntitlementStatus.ACTIVE
                || entitlement.paymentProvider() != ALevelPaymentProvider.COMPLIMENTARY
                || !snapshot.entitlementId().equals(entitlement.entitlementId())
                || !snapshot.assessmentId().equals(entitlement.assessmentId())) {
            throw new IllegalArgumentException("not an active complimentary entitlement with its snapshot");
        }
        // The unique assessment_id column is the final guard against a concurrent second entitlement.
        if (entitlements.findByAssessmentId(entitlement.assessmentId()).isPresent()) {
            throw new IllegalStateException("an entitlement already exists for this assessment");
        }
        entitlements.saveAndFlush(toEntity(entitlement));
        snapshots.saveAndFlush(toSnapshotEntity(snapshot));
    }

    private PurchasedALevelReportSnapshotEntity toSnapshotEntity(PurchasedALevelReportSnapshot snapshot) {
        var entity = new PurchasedALevelReportSnapshotEntity();
        entity.entitlementId = snapshot.entitlementId();
        entity.assessmentId = snapshot.assessmentId();
        entity.reportVersion = snapshot.reportVersion();
        entity.frozenAt = snapshot.frozenAt();
        entity.reportJson = snapshotCodec.write(snapshot.report());
        return entity;
    }

    private void saveEvent(ALevelPaymentEvent event) {
        var entity = new ProcessedALevelPaymentEventEntity();
        entity.paymentProvider = event.provider();
        entity.providerEventId = event.eventId();
        entity.entitlementId = event.entitlementId();
        entity.processedAt = event.occurredAt();
        events.save(entity);
    }

    private ALevelReportEntitlementEntity toEntity(ALevelReportEntitlement value) {
        var entity = new ALevelReportEntitlementEntity();
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

    private ALevelReportEntitlement toStored(ALevelReportEntitlementEntity entity) {
        return new ALevelReportEntitlement(entity.entitlementId, entity.assessmentId, entity.reportTier,
                entity.status, entity.paymentProvider, entity.providerCheckoutSessionId,
                URI.create(entity.checkoutUrl), entity.priceConfigurationKey,
                Optional.ofNullable(entity.providerPaymentReference),
                Optional.ofNullable(entity.purchasedReportVersion), entity.createdAt,
                Optional.ofNullable(entity.confirmedAt));
    }

    private PurchasedALevelReportSnapshot toSnapshot(PurchasedALevelReportSnapshotEntity entity) {
        return new PurchasedALevelReportSnapshot(entity.entitlementId, entity.assessmentId,
                entity.reportVersion, entity.frozenAt,
                snapshotCodec.read(entity.reportVersion, entity.reportJson));
    }
}
