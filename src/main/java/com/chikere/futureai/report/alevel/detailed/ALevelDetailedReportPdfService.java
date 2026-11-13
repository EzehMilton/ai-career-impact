package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfAccessDeniedException;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The PDF of a stored {@code a-level-report-v2}. It reads only the stored snapshot through the ACTIVE-entitlement
 * check and renders it; it never prepares, assembles or calls a provider. A provisional report has no PDF until it is
 * stored. The snapshot records the PDF version it was stored for, and only a renderer of that version may render it.
 */
public final class ALevelDetailedReportPdfService {

    private final ALevelDetailedReportUseCase reports;
    private final Map<String, ALevelDetailedReportPdfRenderer> renderers;
    private final AssessmentTrace trace;

    public ALevelDetailedReportPdfService(ALevelDetailedReportUseCase reports,
            ALevelDetailedReportPdfRenderer renderer) {
        this(reports, List.of(renderer), AssessmentTrace.disabled());
    }

    public ALevelDetailedReportPdfService(ALevelDetailedReportUseCase reports,
            ALevelDetailedReportPdfRenderer renderer, AssessmentTrace trace) {
        this(reports, List.of(renderer), trace);
    }

    public ALevelDetailedReportPdfService(ALevelDetailedReportUseCase reports,
            List<ALevelDetailedReportPdfRenderer> renderers, AssessmentTrace trace) {
        this.reports = Objects.requireNonNull(reports);
        Objects.requireNonNull(renderers);
        this.renderers = renderers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ALevelDetailedReportPdfRenderer::pdfVersion, renderer -> renderer));
        this.trace = Objects.requireNonNull(trace);
    }

    public Pdf render(UUID assessmentId) {
        ALevelDetailedReportSnapshot snapshot = reports.find(Objects.requireNonNull(assessmentId)).orElseThrow(() ->
                new ALevelReportPdfAccessDeniedException());
        ALevelDetailedReportPdfRenderer renderer = java.util.Optional.ofNullable(renderers.get(snapshot.pdfVersion()))
                .orElseThrow(() -> new IllegalStateException("no PDF renderer for " + snapshot.pdfVersion()));
        Pdf pdf = new Pdf(filename(String.join(" ", snapshot.report().subjects())), renderer.render(snapshot.report()));
        trace.scoped(JourneyType.A_LEVEL, assessmentId, "A_LEVEL_DETAILED_REPORT_PDF", () -> {
            trace.event(JourneyType.A_LEVEL, TraceStage.A_LEVEL_REPORT_V2_PDF_RENDERED, () -> {
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

    static String filename(String subjects) {
        String slug = subjects.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "subjects";
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-$", "");
        return "a-level-report-" + slug + ".pdf";
    }
}
