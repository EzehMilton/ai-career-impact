package com.chikere.futureai.pdf.professional.persistence;

import com.chikere.futureai.pdf.professional.application.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalReportPdfStore implements ProfessionalReportPdfStore {
    private final PurchasedProfessionalReportPdfJpaRepository repository;

    JpaProfessionalReportPdfStore(PurchasedProfessionalReportPdfJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchasedProfessionalReportPdf> findByEntitlementId(UUID entitlementId) {
        return repository.findById(entitlementId).map(this::toArtifact);
    }

    @Override
    @Transactional
    public PurchasedProfessionalReportPdf saveIfAbsent(PurchasedProfessionalReportPdf candidate) {
        repository.insertIfAbsent(candidate.entitlementId(), candidate.assessmentId(),
                candidate.purchasedReportVersion(), candidate.pdfTemplateVersion(), candidate.generatedAt(),
                candidate.filename(), candidate.fileSize(), candidate.sha256(), candidate.bytes());
        return repository.findById(candidate.entitlementId())
                .map(this::toArtifact)
                .orElseThrow(() -> new IllegalStateException("PDF artifact was not persisted"));
    }

    private PurchasedProfessionalReportPdf toArtifact(PurchasedProfessionalReportPdfEntity entity) {
        return new PurchasedProfessionalReportPdf(entity.entitlementId, entity.assessmentId,
                entity.purchasedReportVersion, entity.pdfTemplateVersion, entity.generatedAt,
                entity.filename, entity.fileSize, entity.sha256, entity.pdfBytes);
    }
}
