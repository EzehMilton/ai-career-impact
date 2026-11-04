package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfAccessDeniedException;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The PDF of a stored detailed report, in the PDF version it was stored for. It reads only the stored snapshot through the ACTIVE-entitlement
 * check and renders it; it never prepares, assembles or calls a provider. A provisional report has no PDF until it is
 * stored. The snapshot records the PDF version it was stored for, and only a renderer of that version may render it.
 */
public final class UniversityDetailedReportPdfService {

    private final UniversityDetailedReportUseCase reports;
    private final UniversityDetailedReportPdfRenderer renderer;
    private final AssessmentTrace trace;

    public UniversityDetailedReportPdfService(UniversityDetailedReportUseCase reports,
            UniversityDetailedReportPdfRenderer renderer) {
        this(reports, renderer, AssessmentTrace.disabled());
    }

    public UniversityDetailedReportPdfService(UniversityDetailedReportUseCase reports,
            UniversityDetailedReportPdfRenderer renderer, AssessmentTrace trace) {
        this.reports = Objects.requireNonNull(reports);
        this.renderer = Objects.requireNonNull(renderer);
        this.trace = Objects.requireNonNull(trace);
    }

    public Pdf render(UUID assessmentId) {
        UniversityDetailedReportSnapshot snapshot = reports.find(Objects.requireNonNull(assessmentId)).orElseThrow(() ->
                new UniversityReportPdfAccessDeniedException());
        if (!renderer.pdfVersions().contains(snapshot.pdfVersion())) {
            throw new IllegalStateException("stored report needs " + snapshot.pdfVersion() + ", renderer has "
                    + renderer.pdfVersions());
        }
        Pdf pdf = new Pdf(filename(snapshot.report().courseTitle()), renderer.render(snapshot.report()));
        trace.scoped(JourneyType.UNIVERSITY, assessmentId, "UNIVERSITY_REPORT_V2_PDF", () -> {
            trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_REPORT_V2_PDF_RENDERED, () -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("reportId", snapshot.reportId());
                payload.put("reportVersion", snapshot.reportVersion());
                payload.put("pdfVersion", snapshot.pdfVersion());
                payload.put("bytes", pdf.bytes().length);
                payload.put("stored", false);
                return payload;
            });
            return null;
        });
        return pdf;
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
