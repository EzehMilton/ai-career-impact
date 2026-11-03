package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.report.professional.application.ProfessionalScoreDisplayFormatter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Maps a stored detailed report to its shared HTML/PDF view. */
public final class ProfessionalDetailedReportViewMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK)
            .withZone(ZoneId.of("Europe/London"));

    public ProfessionalDetailedReportView toView(ProfessionalDetailedReportSnapshot snapshot) {
        return view(snapshot.report(), snapshot.reportVersion(), snapshot.pdfVersion(), false);
    }

    /** A provisional report (paid narrative retryable): the same presentation, marked as not yet stored. */
    public ProfessionalDetailedReportView toProvisionalView(ProfessionalDetailedReport report) {
        return view(report, report.reportVersion(), ProfessionalDetailedReportVersions.PDF, true);
    }

    private ProfessionalDetailedReportView view(ProfessionalDetailedReport report, String reportVersion,
            String pdfVersion, boolean provisional) {
        return new ProfessionalDetailedReportView(report, DATE.format(report.cover().assessedAt()),
                report.measures().stream().map(measure -> new ProfessionalDetailedReportView.MeasureView(measure.name(),
                        ProfessionalScoreDisplayFormatter.format(measure.indicator(), measure.band()),
                        band(measure.band().name()), measure.explanation())).toList(),
                report.narrativeStatus() == PaidNarrativeStatus.AVAILABLE, reportVersion, pdfVersion, provisional,
                report.responsibilities().cards().stream().map(card -> new ProfessionalDetailedReportView.CategoryNote(
                        card.categoryLabel(), card.explanation())).distinct().toList());
    }

    private static String band(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
