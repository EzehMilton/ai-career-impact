package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.PlanNoteRestatement;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport.Action;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport.Phase;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport.PlanSection;
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
        if (ProfessionalDetailedReportVersions.REPORT_V4.equals(reportVersion)) {
            report = withoutRestatedNotes(report);
        }
        return new ProfessionalDetailedReportView(report, DATE.format(report.cover().assessedAt()),
                report.measures().stream().map(measure -> new ProfessionalDetailedReportView.MeasureView(measure.name(),
                        ProfessionalScoreDisplayFormatter.format(measure.indicator(), measure.band()),
                        band(measure.band().name()), measure.explanation())).toList(),
                report.narrativeStatus() == PaidNarrativeStatus.AVAILABLE, reportVersion, pdfVersion, provisional,
                report.responsibilities().cards().stream().map(card -> new ProfessionalDetailedReportView.CategoryNote(
                        card.categoryLabel(), card.explanation())).distinct().toList());
    }

    /**
     * Stored v4 reports assembled before the assembler dropped restated plan notes may still carry a note that only
     * repeats its action. Hidden here, for display only: the stored document is never changed. v3 is shown as stored.
     */
    private static ProfessionalDetailedReport withoutRestatedNotes(ProfessionalDetailedReport report) {
        PlanSection plan = report.plan();
        PlanSection shown = new PlanSection(plan.introduction(), plan.narrative(), plan.phases().stream()
                .map(phase -> new Phase(phase.heading(), phase.purpose(), phase.actions().stream()
                        .map(action -> action.note() != null && PlanNoteRestatement.restates(action.note(),
                                action.title(), action.rationale(), action.reviewNote())
                                ? new Action(action.title(), action.rationale(), action.reviewNote(), null) : action)
                        .toList()))
                .toList());
        return shown.equals(plan) ? report : new ProfessionalDetailedReport(report.reportVersion(), report.provenance(),
                report.developmentFixture(), report.cover(), report.narrativeStatus(), report.executiveSummary(),
                report.measures(), report.scoresTogether(), report.factors(), report.responsibilities(),
                report.opportunities(), report.humanValue(), report.skills(), report.safeguards(), shown,
                report.adaptationStrategy(), report.limitations(), report.nextSteps(), report.closingPerspective());
    }

    private static String band(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
