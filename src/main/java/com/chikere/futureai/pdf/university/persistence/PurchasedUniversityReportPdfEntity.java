package com.chikere.futureai.pdf.university.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchased_university_report_pdf")
class PurchasedUniversityReportPdfEntity {
    @Id @Column(name = "entitlement_id") UUID entitlementId;
    @Column(name = "assessment_id", nullable = false, unique = true) UUID assessmentId;
    @Column(name = "purchased_report_version", nullable = false) String purchasedReportVersion;
    @Column(name = "pdf_template_version", nullable = false) String pdfTemplateVersion;
    @Column(name = "generated_at", nullable = false) Instant generatedAt;
    @Column(nullable = false) String filename;
    @Column(name = "file_size", nullable = false) long fileSize;
    @Column(nullable = false) String sha256;
    @Column(name = "pdf_bytes", nullable = false) byte[] pdfBytes;

    protected PurchasedUniversityReportPdfEntity() { }
}
