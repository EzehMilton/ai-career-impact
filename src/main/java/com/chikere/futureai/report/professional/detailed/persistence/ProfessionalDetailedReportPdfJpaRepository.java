package com.chikere.futureai.report.professional.detailed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface ProfessionalDetailedReportPdfJpaRepository extends JpaRepository<ProfessionalDetailedReportPdfEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into professional_detailed_report_pdf
              (report_id, assessment_id, report_version, pdf_version, generated_at, filename, file_size, sha256, pdf_bytes)
            values (:reportId, :assessmentId, :reportVersion, :pdfVersion, :generatedAt, :filename, :fileSize, :sha256,
                    :pdfBytes)
            on conflict (report_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("reportId") UUID reportId, @Param("assessmentId") UUID assessmentId,
            @Param("reportVersion") String reportVersion, @Param("pdfVersion") String pdfVersion,
            @Param("generatedAt") Instant generatedAt, @Param("filename") String filename,
            @Param("fileSize") long fileSize, @Param("sha256") String sha256, @Param("pdfBytes") byte[] pdfBytes);
}
