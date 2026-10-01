package com.chikere.futureai.pdf.professional.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchased_professional_report_pdf")
class PurchasedProfessionalReportPdfEntity {
    @Id @Column(name = "entitlement_id") UUID entitlementId;
    @Column(name = "assessment_id", nullable = false, unique = true) UUID assessmentId;
    @Column(name = "purchased_report_version", nullable = false) String purchasedReportVersion;
    @Column(name = "pdf_template_version", nullable = false) String pdfTemplateVersion;
    @Column(name = "generated_at", nullable = false) Instant generatedAt;
    @Column(nullable = false) String filename;
    @Column(name = "file_size", nullable = false) long fileSize;
    @Column(nullable = false, length = 64) String sha256;
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea") byte[] pdfBytes;

    protected PurchasedProfessionalReportPdfEntity() { }
}
