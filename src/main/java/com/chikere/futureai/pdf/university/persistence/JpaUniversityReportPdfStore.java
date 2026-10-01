package com.chikere.futureai.pdf.university.persistence;

import com.chikere.futureai.pdf.university.application.PurchasedUniversityReportPdf;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUniversityReportPdfStore implements UniversityReportPdfStore {
    private final PurchasedUniversityReportPdfJpaRepository repository;

    JpaUniversityReportPdfStore(PurchasedUniversityReportPdfJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchasedUniversityReportPdf> findByEntitlementId(UUID entitlementId) {
        return repository.findById(entitlementId).map(this::toArtifact);
    }

    @Override
    @Transactional
    public PurchasedUniversityReportPdf saveIfAbsent(PurchasedUniversityReportPdf candidate) {
        // ON CONFLICT DO NOTHING: if two first requests race, one immutable artifact wins and both
        // callers return that same persisted artifact.
        repository.insertIfAbsent(candidate.entitlementId(), candidate.assessmentId(),
                candidate.purchasedReportVersion(), candidate.pdfTemplateVersion(), candidate.generatedAt(),
                candidate.filename(), candidate.fileSize(), candidate.sha256(), candidate.bytes());
        return repository.findById(candidate.entitlementId())
                .map(this::toArtifact)
                .orElseThrow(() -> new IllegalStateException("PDF artifact was not persisted"));
    }

    private PurchasedUniversityReportPdf toArtifact(PurchasedUniversityReportPdfEntity entity) {
        return new PurchasedUniversityReportPdf(entity.entitlementId, entity.assessmentId,
                entity.purchasedReportVersion, entity.pdfTemplateVersion, entity.generatedAt,
                entity.filename, entity.fileSize, entity.sha256, entity.pdfBytes);
    }
}
