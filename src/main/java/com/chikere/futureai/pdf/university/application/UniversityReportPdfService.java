package com.chikere.futureai.pdf.university.application;

import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The PDF is a delivery artifact of the exact report already bought.
 *
 * <p>This service reads an ACTIVE entitlement and its frozen purchased snapshot directly through
 * persistence ports. It never calls the report assembler, Jev, the decision policy, OpenAI,
 * narrative generation, evidence retrieval or Stripe.</p>
 */
public final class UniversityReportPdfService {
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final UniversityPurchaseStore purchaseStore;
    private final UniversityReportPdfStore pdfStore;
    private final UniversityReportPdfRenderer renderer;
    private final UniversityReportPdfModelMapper modelMapper;
    private final long maximumBytes;
    private final Clock clock;

    public UniversityReportPdfService(UniversityPurchaseStore purchaseStore, UniversityReportPdfStore pdfStore,
            UniversityReportPdfRenderer renderer, UniversityReportPdfModelMapper modelMapper,
            long maximumBytes, Clock clock) {
        this.purchaseStore = Objects.requireNonNull(purchaseStore);
        this.pdfStore = Objects.requireNonNull(pdfStore);
        this.renderer = Objects.requireNonNull(renderer);
        this.modelMapper = Objects.requireNonNull(modelMapper);
        if (maximumBytes < 1_024) throw new IllegalArgumentException("maximumBytes must be at least 1024");
        this.maximumBytes = maximumBytes;
        this.clock = Objects.requireNonNull(clock);
    }

    public PurchasedUniversityReportPdf getOrCreate(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        UniversityReportEntitlement entitlement = purchaseStore.findEntitlementByAssessmentId(assessmentId)
                .filter(value -> value.status() == UniversityEntitlementStatus.ACTIVE)
                .orElseThrow(UniversityReportPdfAccessDeniedException::new);
        var existing = pdfStore.findByEntitlementId(entitlement.entitlementId());
        if (existing.isPresent()) {
            // An existing artifact is returned byte-for-byte without loading or re-rendering anything.
            return existing.orElseThrow();
        }
        PurchasedUniversityReportSnapshot snapshot = purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .orElseThrow(() -> new UniversityReportPdfGenerationException(
                        "purchased report snapshot is unavailable"));
        if (!snapshot.entitlementId().equals(entitlement.entitlementId())) {
            throw new UniversityReportPdfGenerationException(
                    "purchased report snapshot does not match entitlement");
        }
        byte[] bytes;
        try {
            bytes = renderer.render(modelMapper.from(snapshot)).bytes();
        }
        catch (RuntimeException failure) {
            throw new UniversityReportPdfGenerationException(
                    "purchased report PDF could not be rendered", failure);
        }
        validate(bytes);
        var artifact = new PurchasedUniversityReportPdf(entitlement.entitlementId(), assessmentId,
                snapshot.reportVersion(), UniversityReportPdfVersions.PDF_TEMPLATE, Instant.now(clock),
                safeFilename(snapshot.report().courseTitle()), bytes.length, sha256(bytes), bytes);
        return pdfStore.saveIfAbsent(artifact);
    }

    private void validate(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length) {
            throw new UniversityReportPdfGenerationException("rendered PDF is empty or incomplete");
        }
        if (bytes.length > maximumBytes) {
            throw new UniversityReportPdfGenerationException("rendered PDF exceeds configured maximum size");
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                throw new UniversityReportPdfGenerationException("renderer output is not a PDF");
            }
        }
    }

    static String safeFilename(String courseTitle) {
        String slug = courseTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) slug = "course";
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-+$", "");
        return "ai-career-impact-" + slug + "-course-report.pdf";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
