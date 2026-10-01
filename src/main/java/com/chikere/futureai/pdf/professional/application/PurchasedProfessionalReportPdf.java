package com.chikere.futureai.pdf.professional.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PurchasedProfessionalReportPdf(
        UUID entitlementId,
        UUID assessmentId,
        String purchasedReportVersion,
        String pdfTemplateVersion,
        Instant generatedAt,
        String filename,
        long fileSize,
        String sha256,
        byte[] bytes) {

    public PurchasedProfessionalReportPdf {
        Objects.requireNonNull(entitlementId); Objects.requireNonNull(assessmentId);
        purchasedReportVersion = required(purchasedReportVersion, "purchasedReportVersion");
        pdfTemplateVersion = required(pdfTemplateVersion, "pdfTemplateVersion");
        Objects.requireNonNull(generatedAt);
        filename = required(filename, "filename"); sha256 = required(sha256, "sha256");
        bytes = Objects.requireNonNull(bytes).clone();
        if (bytes.length == 0 || fileSize != bytes.length) throw new IllegalArgumentException("fileSize must match bytes");
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
