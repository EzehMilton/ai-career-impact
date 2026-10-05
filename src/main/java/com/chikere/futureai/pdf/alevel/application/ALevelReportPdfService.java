package com.chikere.futureai.pdf.alevel.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.PurchasedALevelReportSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
public final class ALevelReportPdfService implements ALevelReportPdfUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelReportPdfService.class);
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private final ALevelPurchaseStore purchaseStore;
    private final ALevelReportPdfStore pdfStore;
    private final ALevelReportPdfRenderer renderer;
    private final ALevelReportPdfModelMapper modelMapper;
    private final long maximumBytes;
    private final Clock clock;

    public ALevelReportPdfService(ALevelPurchaseStore purchaseStore, ALevelReportPdfStore pdfStore,
            ALevelReportPdfRenderer renderer, ALevelReportPdfModelMapper modelMapper,
            long maximumBytes, Clock clock) {
        this.purchaseStore = Objects.requireNonNull(purchaseStore);
        this.pdfStore = Objects.requireNonNull(pdfStore);
        this.renderer = Objects.requireNonNull(renderer);
        this.modelMapper = Objects.requireNonNull(modelMapper);
        if (maximumBytes < 1_024) throw new IllegalArgumentException("maximumBytes must be at least 1024");
        this.maximumBytes = maximumBytes;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PurchasedALevelReportPdf getOrCreate(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        ALevelReportEntitlement entitlement = purchaseStore.findEntitlementByAssessmentId(assessmentId)
                .filter(value -> value.status() == ALevelEntitlementStatus.ACTIVE)
                .orElseThrow(ALevelReportPdfAccessDeniedException::new);
        var existing = pdfStore.findByEntitlementId(entitlement.entitlementId());
        if (existing.isPresent()) {
            // An existing artifact is returned byte-for-byte without loading or re-rendering anything.
            return existing.orElseThrow();
        }
        PurchasedALevelReportSnapshot snapshot = purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .orElseThrow(() -> new ALevelReportPdfGenerationException(
                        "purchased report snapshot is unavailable"));
        if (!snapshot.entitlementId().equals(entitlement.entitlementId())) {
            throw new ALevelReportPdfGenerationException("purchased report snapshot does not match entitlement");
        }
        byte[] bytes;
        try {
            bytes = renderer.render(modelMapper.from(snapshot)).bytes();
        }
        catch (RuntimeException failure) {
            throw new ALevelReportPdfGenerationException("purchased report PDF could not be rendered", failure);
        }
        validate(bytes);
        var artifact = new PurchasedALevelReportPdf(entitlement.entitlementId(), assessmentId,
                snapshot.reportVersion(), ALevelReportPdfVersions.PDF_TEMPLATE, Instant.now(clock),
                safeFilename(snapshot.report().selectedSubjects()), bytes.length, sha256(bytes), bytes);
        PurchasedALevelReportPdf stored = pdfStore.saveIfAbsent(artifact);
        LOGGER.info("PDF journey=alevel assessmentId={} entitlementId={} outcome=AVAILABLE byteCount={}",
                assessmentId, entitlement.entitlementId(), stored.fileSize());
        return stored;
    }

    private void validate(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length) {
            throw new ALevelReportPdfGenerationException("rendered PDF is empty or incomplete");
        }
        if (bytes.length > maximumBytes) {
            throw new ALevelReportPdfGenerationException("rendered PDF exceeds configured maximum size");
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                throw new ALevelReportPdfGenerationException("renderer output is not a PDF");
            }
        }
    }

    static String safeFilename(List<String> subjects) {
        String slug = String.join("-", subjects).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) slug = "subjects";
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-+$", "");
        return "ai-career-impact-" + slug + "-a-level-report.pdf";
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
