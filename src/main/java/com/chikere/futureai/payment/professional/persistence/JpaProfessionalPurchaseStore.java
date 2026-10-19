package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.payment.professional.application.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalPurchaseStore implements ProfessionalPurchaseStore {
    private final ProfessionalReportEntitlementJpaRepository entitlements;
    private final PurchasedProfessionalReportSnapshotJpaRepository snapshots;
    private final ProcessedProfessionalPaymentEventJpaRepository events;
    private final PurchasedProfessionalReportSnapshotCodec snapshotCodec;

    JpaProfessionalPurchaseStore(ProfessionalReportEntitlementJpaRepository entitlements,
            PurchasedProfessionalReportSnapshotJpaRepository snapshots,
            ProcessedProfessionalPaymentEventJpaRepository events,
            PurchasedProfessionalReportSnapshotCodec snapshotCodec) {
        this.entitlements = entitlements;
        this.snapshots = snapshots;
        this.events = events;
        this.snapshotCodec = snapshotCodec;
    }

    @Override
    @Transactional
    public void savePending(ProfessionalReportEntitlement entitlement) {
        if (entitlement.status() != ProfessionalEntitlementStatus.PENDING) {
            throw new IllegalArgumentException("only pending entitlements can be created");
        }
        var existing = entitlements.findByAssessmentId(entitlement.assessmentId());
        if (existing.isPresent()) {
            var entity = existing.orElseThrow();
            if (entity.status != ProfessionalEntitlementStatus.FAILED
                    || !entity.entitlementId.equals(entitlement.entitlementId())) {
                throw new IllegalStateException("an entitlement already exists for this assessment");
            }
            entity.status = ProfessionalEntitlementStatus.PENDING;
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
            ProfessionalCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt) {
        var entity = entitlements.findByIdForUpdate(entitlementId)
                .orElseThrow(() -> new IllegalStateException("entitlement does not exist"));
        // A concurrent retry with the same idempotency key already stored this exact session.
        if (replacement.checkoutSessionId().equals(entity.providerCheckoutSessionId)) return;
        if (entity.status != ProfessionalEntitlementStatus.PENDING
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
    public Optional<ProfessionalReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId) {
        return entitlements.findByAssessmentId(assessmentId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalReportEntitlement> findEntitlementById(UUID entitlementId) {
        return entitlements.findById(entitlementId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchasedProfessionalReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId) {
        return snapshots.findByAssessmentId(assessmentId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEventProcessed(ProfessionalPaymentProvider provider, String eventId) {
        return events.existsById(new ProcessedProfessionalPaymentEventId(provider, eventId));
    }

    @Override
    @Transactional
    public PurchaseConfirmationResult confirmAndFreeze(ProfessionalPaymentEvent event,
            PurchasedProfessionalReportSnapshot snapshot) {
        var eventId = new ProcessedProfessionalPaymentEventId(event.provider(), event.eventId());
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        var entitlement = entitlements.findByIdForUpdate(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException("ENTITLEMENT_NOT_FOUND"));
        // A concurrent duplicate may have committed while this transaction waited for the row lock.
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        if (entitlement.status == ProfessionalEntitlementStatus.ACTIVE) {
            saveEvent(event);
            return PurchaseConfirmationResult.ALREADY_PROCESSED;
        }
        if (entitlement.status != ProfessionalEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("ENTITLEMENT_STATUS_INVALID");
        }
        if (!entitlement.assessmentId.equals(event.assessmentId())
                || entitlement.paymentProvider != event.provider()
                || !entitlement.providerCheckoutSessionId.equals(event.checkoutSessionId())
                || !snapshot.entitlementId().equals(entitlement.entitlementId)
                || !snapshot.assessmentId().equals(entitlement.assessmentId)) {
            throw new InvalidPaymentNotificationException("ENTITLEMENT_REFERENCE_MISMATCH");
        }
        snapshots.save(toSnapshotEntity(snapshot));

        entitlement.status = ProfessionalEntitlementStatus.ACTIVE;
        entitlement.providerPaymentReference = event.paymentReference().orElse(null);
        entitlement.purchasedReportVersion = snapshot.reportVersion();
        entitlement.confirmedAt = snapshot.frozenAt();
        entitlements.save(entitlement);
        saveEvent(event);
        snapshots.flush(); entitlements.flush(); events.flush();
        return PurchaseConfirmationResult.ACTIVATED;
    }

    @Override
    @Transactional
    public void saveComplimentary(ProfessionalReportEntitlement entitlement, PurchasedProfessionalReportSnapshot snapshot) {
        if (entitlement.status() != ProfessionalEntitlementStatus.ACTIVE
                || entitlement.paymentProvider() != ProfessionalPaymentProvider.COMPLIMENTARY
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

    private PurchasedProfessionalReportSnapshotEntity toSnapshotEntity(PurchasedProfessionalReportSnapshot snapshot) {
        var entity = new PurchasedProfessionalReportSnapshotEntity();
        entity.entitlementId = snapshot.entitlementId();
        entity.assessmentId = snapshot.assessmentId();
        entity.reportVersion = snapshot.reportVersion();
        entity.frozenAt = snapshot.frozenAt();
        entity.reportJson = snapshotCodec.write(snapshot.report());
        return entity;
    }

    private void saveEvent(ProfessionalPaymentEvent event) {
        var entity = new ProcessedProfessionalPaymentEventEntity();
        entity.paymentProvider = event.provider();
        entity.providerEventId = event.eventId();
        entity.entitlementId = event.entitlementId();
        entity.processedAt = event.occurredAt();
        events.save(entity);
    }

    private ProfessionalReportEntitlementEntity toEntity(ProfessionalReportEntitlement value) {
        var entity = new ProfessionalReportEntitlementEntity();
        entity.entitlementId = value.entitlementId(); entity.assessmentId = value.assessmentId();
        entity.reportTier = value.reportTier(); entity.status = value.status();
        entity.paymentProvider = value.paymentProvider();
        entity.providerCheckoutSessionId = value.providerCheckoutSessionId();
        entity.checkoutUrl = value.checkoutUrl().toString();
        entity.priceConfigurationKey = value.priceConfigurationKey();
        entity.providerPaymentReference = value.providerPaymentReference().orElse(null);
        entity.purchasedReportVersion = value.purchasedReportVersion().orElse(null);
        entity.createdAt = value.createdAt(); entity.confirmedAt = value.confirmedAt().orElse(null);
        return entity;
    }

    private ProfessionalReportEntitlement toStored(ProfessionalReportEntitlementEntity entity) {
        return new ProfessionalReportEntitlement(entity.entitlementId, entity.assessmentId, entity.reportTier,
                entity.status, entity.paymentProvider, entity.providerCheckoutSessionId, URI.create(entity.checkoutUrl),
                entity.priceConfigurationKey, Optional.ofNullable(entity.providerPaymentReference),
                Optional.ofNullable(entity.purchasedReportVersion), entity.createdAt,
                Optional.ofNullable(entity.confirmedAt));
    }

    private PurchasedProfessionalReportSnapshot toSnapshot(PurchasedProfessionalReportSnapshotEntity entity) {
        return new PurchasedProfessionalReportSnapshot(entity.entitlementId, entity.assessmentId,
                entity.reportVersion, entity.frozenAt, snapshotCodec.read(entity.reportVersion, entity.reportJson));
    }
}
