package com.chikere.futureai.report.professional.detailed;

import java.util.List;

/**
 * The one presentation model of a stored detailed report, shared by the web page and the PDF so both show the same
 * authoritative content. Only formatting happens here: scores use the boundary-safe display formatter and bands
 * become words; nothing is recalculated or reordered. {@code provisional} marks a report shown while its paid
 * narrative is retryable: complete, but not yet stored.
 */
public record ProfessionalDetailedReportView(
        ProfessionalDetailedReport report,
        String assessedOn,
        List<MeasureView> measures,
        boolean narrativeAvailable,
        String reportVersion,
        String pdfVersion,
        boolean provisional,
        List<CategoryNote> categoryNotes) {

    public ProfessionalDetailedReportView {
        measures = List.copyOf(measures);
        categoryNotes = List.copyOf(categoryNotes);
    }

    public record MeasureView(String name, String score, String band, String explanation) {
    }

    /**
     * v4 presentation: each distinct stored classification sentence once, under its label, in order of first use,
     * instead of the same sentence on every responsibility card that shares it.
     */
    public record CategoryNote(String label, String explanation) {
    }
}
