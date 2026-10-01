package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalPurchaseStore implements ProfessionalPurchaseStore {
    private final ProfessionalReportEntitlementJpaRepository entitlements;
    private final PurchasedProfessionalReportSnapshotJpaRepository snapshots;
    private final ProcessedProfessionalPaymentEventJpaRepository events;
    private final ObjectMapper objectMapper;

    JpaProfessionalPurchaseStore(ProfessionalReportEntitlementJpaRepository entitlements,
            PurchasedProfessionalReportSnapshotJpaRepository snapshots,
            ProcessedProfessionalPaymentEventJpaRepository events, ObjectMapper objectMapper) {
        this.entitlements = entitlements;
        this.snapshots = snapshots;
        this.events = events;
        this.objectMapper = objectMapper;
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
                .orElseThrow(() -> new InvalidPaymentNotificationException("entitlement no longer exists"));
        // A concurrent duplicate may have committed while this transaction waited for the row lock.
        if (events.existsById(eventId)) return PurchaseConfirmationResult.ALREADY_PROCESSED;
        if (entitlement.status == ProfessionalEntitlementStatus.ACTIVE) {
            saveEvent(event);
            return PurchaseConfirmationResult.ALREADY_PROCESSED;
        }
        if (entitlement.status != ProfessionalEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("entitlement is not pending");
        }
        if (!entitlement.assessmentId.equals(event.assessmentId())
                || entitlement.paymentProvider != event.provider()
                || !entitlement.providerCheckoutSessionId.equals(event.checkoutSessionId())
                || !snapshot.entitlementId().equals(entitlement.entitlementId)
                || !snapshot.assessmentId().equals(entitlement.assessmentId)) {
            throw new InvalidPaymentNotificationException("payment confirmation does not match entitlement");
        }
        var snapshotEntity = new PurchasedProfessionalReportSnapshotEntity();
        snapshotEntity.entitlementId = snapshot.entitlementId();
        snapshotEntity.assessmentId = snapshot.assessmentId();
        snapshotEntity.reportVersion = snapshot.reportVersion();
        snapshotEntity.frozenAt = snapshot.frozenAt();
        snapshotEntity.reportJson = objectMapper.valueToTree(snapshot.report());
        snapshots.save(snapshotEntity);

        entitlement.status = ProfessionalEntitlementStatus.ACTIVE;
        entitlement.providerPaymentReference = event.paymentReference().orElse(null);
        entitlement.purchasedReportVersion = snapshot.reportVersion();
        entitlement.confirmedAt = snapshot.frozenAt();
        entitlements.save(entitlement);
        saveEvent(event);
        snapshots.flush(); entitlements.flush(); events.flush();
        return PurchaseConfirmationResult.ACTIVATED;
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
        try {
            ProfessionalReport report = objectMapper.treeToValue(entity.reportJson, ProfessionalReport.class);
            return new PurchasedProfessionalReportSnapshot(entity.entitlementId, entity.assessmentId,
                    entity.reportVersion, entity.frozenAt, report);
        }
        catch (JsonProcessingException invalidSnapshot) {
            throw new IllegalStateException("stored purchased report snapshot is invalid", invalidSnapshot);
        }
    }
}
