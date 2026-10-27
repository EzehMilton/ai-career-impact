package com.chikere.futureai.report.professional.detailed.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professional_detailed_report_pdf")
class ProfessionalDetailedReportPdfEntity {
    @Id @Column(name = "report_id") UUID reportId;
    @Column(name = "assessment_id", nullable = false) UUID assessmentId;
    @Column(name = "report_version", nullable = false) String reportVersion;
    @Column(name = "pdf_version", nullable = false) String pdfVersion;
    @Column(name = "generated_at", nullable = false) Instant generatedAt;
    @Column(nullable = false) String filename;
    @Column(name = "file_size", nullable = false) long fileSize;
    @Column(nullable = false, length = 64) String sha256;
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea") byte[] pdfBytes;

    protected ProfessionalDetailedReportPdfEntity() {}
}
