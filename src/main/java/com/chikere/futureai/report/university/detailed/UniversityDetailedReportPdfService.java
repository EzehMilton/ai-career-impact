package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.pdf.university.application.UniversityReportPdfAccessDeniedException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The PDF of a stored {@code university-report-v2}. It reads only the stored snapshot through the ACTIVE-entitlement
 * check and renders it; it never prepares, assembles or calls a provider. A provisional report has no PDF until it is
 * stored. The snapshot records the PDF version it was stored for, and only a renderer of that version may render it.
 */
public final class UniversityDetailedReportPdfService {

    private final UniversityDetailedReportUseCase reports;
    private final UniversityDetailedReportPdfRenderer renderer;

    public UniversityDetailedReportPdfService(UniversityDetailedReportUseCase reports,
            UniversityDetailedReportPdfRenderer renderer) {
        this.reports = Objects.requireNonNull(reports);
        this.renderer = Objects.requireNonNull(renderer);
    }

    public Pdf render(UUID assessmentId) {
        UniversityDetailedReportSnapshot snapshot = reports.find(Objects.requireNonNull(assessmentId)).orElseThrow(() ->
                new UniversityReportPdfAccessDeniedException());
        if (!renderer.pdfVersion().equals(snapshot.pdfVersion())) {
            throw new IllegalStateException("stored report needs " + snapshot.pdfVersion() + ", renderer is "
                    + renderer.pdfVersion());
        }
        return new Pdf(filename(snapshot.report().courseTitle()), renderer.render(snapshot.report()));
    }

    public record Pdf(String filename, byte[] bytes) {
        public Pdf {
            Objects.requireNonNull(filename);
            Objects.requireNonNull(bytes);
        }
    }

    static String filename(String courseTitle) {
        String slug = courseTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "course";
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-$", "");
        return "university-report-" + slug + ".pdf";
    }
}
