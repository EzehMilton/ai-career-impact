package com.chikere.futureai.pdf.professional.application;

import com.chikere.futureai.payment.professional.application.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class ProfessionalReportPdfService {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final ProfessionalPurchaseStore purchaseStore;
    private final ProfessionalReportPdfStore pdfStore;
    private final ProfessionalReportPdfRenderer renderer;
    private final ProfessionalReportPdfModelMapper modelMapper;
    private final long maximumBytes;
    private final Clock clock;

    public ProfessionalReportPdfService(ProfessionalPurchaseStore purchaseStore, ProfessionalReportPdfStore pdfStore,
            ProfessionalReportPdfRenderer renderer, ProfessionalReportPdfModelMapper modelMapper,
            long maximumBytes, Clock clock) {
        this.purchaseStore = Objects.requireNonNull(purchaseStore); this.pdfStore = Objects.requireNonNull(pdfStore);
        this.renderer = Objects.requireNonNull(renderer); this.modelMapper = Objects.requireNonNull(modelMapper);
        if (maximumBytes < 1_024) throw new IllegalArgumentException("maximumBytes must be at least 1024");
        this.maximumBytes = maximumBytes; this.clock = Objects.requireNonNull(clock);
    }

    public PurchasedProfessionalReportPdf getOrCreate(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        ProfessionalReportEntitlement entitlement = purchaseStore.findEntitlementByAssessmentId(assessmentId)
                .filter(value -> value.status() == ProfessionalEntitlementStatus.ACTIVE)
                .orElseThrow(ProfessionalReportPdfAccessDeniedException::new);
        var existing = pdfStore.findByEntitlementId(entitlement.entitlementId());
        if (existing.isPresent()) return existing.orElseThrow();
        PurchasedProfessionalReportSnapshot snapshot = purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .orElseThrow(() -> new ProfessionalReportPdfGenerationException("purchased report snapshot is unavailable"));
        if (!snapshot.entitlementId().equals(entitlement.entitlementId())) {
            throw new ProfessionalReportPdfGenerationException("purchased report snapshot does not match entitlement");
        }
        byte[] bytes;
        try {
            bytes = renderer.render(modelMapper.from(snapshot)).bytes();
        }
        catch (RuntimeException failure) {
            throw new ProfessionalReportPdfGenerationException("purchased report PDF could not be rendered", failure);
        }
        validate(bytes);
        var artifact = new PurchasedProfessionalReportPdf(entitlement.entitlementId(), assessmentId,
                snapshot.reportVersion(), ProfessionalReportPdfVersions.PDF_TEMPLATE, Instant.now(clock),
                safeFilename(snapshot.report().profession()), bytes.length, sha256(bytes), bytes);
        return pdfStore.saveIfAbsent(artifact);
    }

    private void validate(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length) throw new ProfessionalReportPdfGenerationException("rendered PDF is empty or incomplete");
        if (bytes.length > maximumBytes) throw new ProfessionalReportPdfGenerationException("rendered PDF exceeds configured maximum size");
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) throw new ProfessionalReportPdfGenerationException("renderer output is not a PDF");
        }
    }

    static String safeFilename(String profession) {
        String slug = profession.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) slug = "professional";
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-+$", "");
        return "ai-career-impact-" + slug + "-report.pdf";
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
}
