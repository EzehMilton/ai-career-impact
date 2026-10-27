package com.chikere.futureai.report.professional.detailed.persistence;

import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportPdfStore;
import com.chikere.futureai.report.professional.detailed.StoredProfessionalDetailedReportPdf;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaProfessionalDetailedReportPdfStore implements ProfessionalDetailedReportPdfStore {

    private final ProfessionalDetailedReportPdfJpaRepository repository;

    JpaProfessionalDetailedReportPdfStore(ProfessionalDetailedReportPdfJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProfessionalDetailedReportPdf> findByReportId(UUID reportId) {
        return repository.findById(reportId).map(JpaProfessionalDetailedReportPdfStore::toArtifact);
    }

    @Override
    @Transactional
    public StoredProfessionalDetailedReportPdf saveIfAbsent(StoredProfessionalDetailedReportPdf candidate) {
        repository.insertIfAbsent(candidate.reportId(), candidate.assessmentId(), candidate.reportVersion(),
                candidate.pdfVersion(), candidate.generatedAt(), candidate.filename(), candidate.fileSize(),
                candidate.sha256(), candidate.bytes());
        return repository.findById(candidate.reportId()).map(JpaProfessionalDetailedReportPdfStore::toArtifact)
                .orElseThrow(() -> new IllegalStateException("detailed PDF was not persisted"));
    }

    private static StoredProfessionalDetailedReportPdf toArtifact(ProfessionalDetailedReportPdfEntity entity) {
        return new StoredProfessionalDetailedReportPdf(entity.reportId, entity.assessmentId, entity.reportVersion,
                entity.pdfVersion, entity.generatedAt, entity.filename, entity.fileSize, entity.sha256, entity.pdfBytes);
    }
}
