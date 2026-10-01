package com.chikere.futureai.pdf.alevel.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface PurchasedALevelReportPdfJpaRepository
        extends JpaRepository<PurchasedALevelReportPdfEntity, UUID> {
    @Modifying
    @Query(value = """
            insert into purchased_a_level_report_pdf
              (entitlement_id, assessment_id, purchased_report_version, pdf_template_version,
               generated_at, filename, file_size, sha256, pdf_bytes)
            values (:entitlementId, :assessmentId, :reportVersion, :pdfVersion,
                    :generatedAt, :filename, :fileSize, :sha256, :pdfBytes)
            on conflict (entitlement_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("entitlementId") UUID entitlementId,
            @Param("assessmentId") UUID assessmentId,
            @Param("reportVersion") String reportVersion,
            @Param("pdfVersion") String pdfVersion,
            @Param("generatedAt") Instant generatedAt,
            @Param("filename") String filename,
            @Param("fileSize") long fileSize,
            @Param("sha256") String sha256,
            @Param("pdfBytes") byte[] pdfBytes);
}
