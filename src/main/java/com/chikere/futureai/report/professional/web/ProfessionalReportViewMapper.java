package com.chikere.futureai.report.professional.web;

import com.chikere.futureai.report.professional.application.ProfessionalScoreDisplayFormatter;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.WorkFocusCategory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;

@Component
public final class ProfessionalReportViewMapper {
    /**
     * Display order only: HIGH_PRIORITY before USEFUL (declaration order), otherwise as selected, since the
     * sort is stable. The responsibility-review guidance only repeats the visitor's own list back to them, so
     * it is not shown; it remains in the stored report.
     */
    static final Comparator<ProfessionalReport.SkillDevelopmentPriority> HIGH_PRIORITY_FIRST =
            Comparator.comparing(ProfessionalReport.SkillDevelopmentPriority::priority);

    public ProfessionalReportViewModel toView(ProfessionalReport report) {
        var summary = report.assessmentSummary();
        var narrative = report.narrative();
        var evidence = report.evidence();
        return new ProfessionalReportViewModel(report.profession(), report.headline(),
                customerStatus(summary.status()),
                summary.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED,
                report.developmentFixture(), summary.interpretation(), summary.jobLossDisclaimer(),
                summary.methodologyCaveat(), report.measures().stream().map(value ->
                    new ProfessionalReportViewModel.MeasureView(measureLabel(value.measure().name()),
                            ProfessionalScoreDisplayFormatter.format(value.indicator(), value.band()), label(value.band().name()),
                            value.conciseExplanation(), value.detailedExplanation().orElse(null))).toList(),
                report.exposureDrivers(), report.protectiveFactors(),
                new ProfessionalReportViewModel.NarrativeView(narrative.available(),
                        narrative.summary().orElse(null), narrative.whatThisMeans().orElse(null),
                        narrative.exposureExplanation().orElse(null),
                        narrative.humanAdvantageExplanation().orElse(null),
                        narrative.complementarityExplanation().orElse(null),
                        narrative.adaptabilityExplanation().orElse(null)),
                report.practicalActions(), report.skillPriorities().stream()
                        .sorted(HIGH_PRIORITY_FIRST)
                        .map(value -> new ProfessionalReportViewModel.SkillView(label(value.priority().name()),
                            value.title(), value.rationale())).toList(),
                report.workFocus().areas().stream()
                        .filter(value -> value.category() != WorkFocusCategory.USER_REVIEW_REQUIRED)
                        .map(value ->
                    new ProfessionalReportViewModel.WorkFocusView(label(value.category().name()), value.title(),
                            value.guidance(), value.relatedResponsibilities())).toList(),
                report.workFocus().limitation(),
                new ProfessionalReportViewModel.EvidenceView(label(evidence.status().name()), evidence.message(),
                        evidence.items().stream().map(this::evidenceItem).toList()),
                report.occupationClassification().map(value ->
                    new ProfessionalReportViewModel.ClassificationView(value.canonicalOccupation(),
                            value.classificationSystem().replace('_', ' '), value.classificationCode(),
                            label(value.quality()), value.classifiedAt().toString().substring(0, 10))).orElse(null),
                new ProfessionalReportViewModel.MethodologyView(
                        report.methodology().judgmentSchemaVersion(), report.methodology().decisionPolicyVersion(),
                        report.methodology().narrativeVersion().orElse(null),
                        report.methodology().evidenceVersion().orElse(null),
                        report.methodology().classifierVersion().orElse(null), report.methodology().reportVersion(),
                        report.methodology().limitations()));
    }

    private ProfessionalReportViewModel.EvidenceItemView evidenceItem(ProfessionalReport.EvidenceItem item) {
        String source = item.publisher().map(value -> item.sourceName() + " · " + value).orElse(item.sourceName());
        String date = item.publicationDate().map(Object::toString)
                .orElse("Retrieved " + item.retrievedAt().toString().substring(0, 10));
        String metric = item.metricValue().map(value -> item.metricName().orElseThrow() + ": "
                + value.stripTrailingZeros().toPlainString() + " " + item.metricUnit().orElseThrow()).orElse(null);
        return new ProfessionalReportViewModel.EvidenceItemView(label(item.category()), item.title(), item.summary(),
                source, item.sourceUrl().map(Object::toString).orElse(null), item.geography().orElse(null),
                date, label(item.sourceQuality().name()), metric, item.relevanceNote());
    }

    /**
     * The status a visitor sees. Confidence and caution are being redesigned, so both READY statuses read
     * simply as complete; the stored status and its calculation are unchanged.
     */
    private String customerStatus(AssessmentStatus status) {
        return status == AssessmentStatus.MORE_INFORMATION_REQUIRED ? label(status.name()) : "Assessment complete";
    }

    private String measureLabel(String name) {
        return switch (name) {
            case "AI_EXPOSURE" -> "AI Exposure";
            case "HUMAN_ADVANTAGE" -> "Human Advantage";
            case "AI_COMPLEMENTARITY" -> "AI Complementarity";
            case "ROLE_ADAPTABILITY" -> "Role Adaptability";
            default -> label(name);
        };
    }

    private String label(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
