package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore;
import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfGenerationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves the {@value ProfessionalDetailedReportVersions#PDF} PDF of a stored detailed report: rendered once from the
 * stored report through the same view as the web page, then the stored bytes are returned on every later request.
 * Nothing is regenerated: no provider, policy or narrative is involved.
 */
public final class ProfessionalDetailedReportPdfService implements ProfessionalDetailedReportPdfUseCase {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ProfessionalPurchaseStore purchases;
    private final ProfessionalDetailedReportStore reports;
    private final ProfessionalDetailedReportUseCase detailedReports;
    private final ProfessionalDetailedReportPdfStore pdfs;
    private final ProfessionalDetailedReportViewMapper views;
    private final ProfessionalDetailedReportPdfRenderer renderer;
    private final long maximumBytes;
    private final Clock clock;

    public ProfessionalDetailedReportPdfService(ProfessionalPurchaseStore purchases,
            ProfessionalDetailedReportStore reports, ProfessionalDetailedReportUseCase detailedReports,
            ProfessionalDetailedReportPdfStore pdfs,
            ProfessionalDetailedReportViewMapper views, ProfessionalDetailedReportPdfRenderer renderer,
            long maximumBytes, Clock clock) {
        this.purchases = Objects.requireNonNull(purchases);
        this.reports = Objects.requireNonNull(reports);
        this.detailedReports = Objects.requireNonNull(detailedReports);
        this.pdfs = Objects.requireNonNull(pdfs);
        this.views = Objects.requireNonNull(views);
        this.renderer = Objects.requireNonNull(renderer);
        this.maximumBytes = maximumBytes;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<DetailedReportPdfDownload> findOrCreate(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        purchases.findEntitlementByAssessmentId(assessmentId)
                .filter(entitlement -> entitlement.status() == ProfessionalEntitlementStatus.ACTIVE)
                .orElseThrow(ProfessionalReportPdfAccessDeniedException::new);
        Optional<ProfessionalDetailedReportSnapshot> report = reports.findByAssessmentId(assessmentId);
        if (report.isEmpty()) {
            // A provisional report is rendered for this download only; nothing is stored until the report is final.
            return detailedReports.provisional(assessmentId).map(provisional -> new DetailedReportPdfDownload(
                    filename(provisional.cover().profession()), render(views.toProvisionalView(provisional)), true));
        }
        var snapshot = report.get();
        var existing = pdfs.findByReportId(snapshot.reportId());
        if (existing.isPresent()) {
            return existing.map(ProfessionalDetailedReportPdfService::download);
        }
        byte[] bytes = render(views.toView(snapshot));
        return Optional.of(download(pdfs.saveIfAbsent(new StoredProfessionalDetailedReportPdf(snapshot.reportId(),
                assessmentId, snapshot.reportVersion(), snapshot.pdfVersion(), Instant.now(clock),
                filename(snapshot.report().cover().profession()), bytes.length, sha256(bytes), bytes))));
    }

    private byte[] render(ProfessionalDetailedReportView view) {
        byte[] bytes;
        try {
            bytes = renderer.render(view);
        }
        catch (RuntimeException failure) {
            throw new ProfessionalReportPdfGenerationException("detailed report PDF could not be rendered", failure);
        }
        validate(bytes);
        return bytes;
    }

    private static DetailedReportPdfDownload download(StoredProfessionalDetailedReportPdf stored) {
        return new DetailedReportPdfDownload(stored.filename(), stored.bytes(), false);
    }

    private void validate(byte[] bytes) {
        if (bytes.length < PDF_SIGNATURE.length || bytes.length > maximumBytes) {
            throw new ProfessionalReportPdfGenerationException("rendered detailed PDF is empty or too large");
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                throw new ProfessionalReportPdfGenerationException("renderer output is not a PDF");
            }
        }
    }

    static String filename(String profession) {
        String slug = profession.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            slug = "professional";
        }
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return "ai-career-impact-" + slug + "-detailed-report.pdf";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
