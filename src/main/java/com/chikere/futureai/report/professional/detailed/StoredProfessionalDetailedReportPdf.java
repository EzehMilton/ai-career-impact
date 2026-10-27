package com.chikere.futureai.report.professional.detailed;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The PDF of one stored detailed report, rendered once and kept as immutable bytes. */
public record StoredProfessionalDetailedReportPdf(
        UUID reportId,
        UUID assessmentId,
        String reportVersion,
        String pdfVersion,
        Instant generatedAt,
        String filename,
        long fileSize,
        String sha256,
        byte[] bytes) {

    public StoredProfessionalDetailedReportPdf {
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(pdfVersion);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(sha256);
        bytes = Objects.requireNonNull(bytes).clone();
        if (bytes.length == 0 || fileSize != bytes.length) {
            throw new IllegalArgumentException("fileSize must match bytes");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
