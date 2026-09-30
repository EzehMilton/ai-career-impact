package com.chikere.futureai.report.professional.web;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class ProfessionalReportViewMapper {
    public ProfessionalReportViewModel toView(ProfessionalReport report) {
        var summary = report.assessmentSummary();
        var narrative = report.narrative();
        var evidence = report.evidence();
        return new ProfessionalReportViewModel(report.profession(), report.headline(),
                label(summary.status().name()), label(summary.confidence().name()),
                summary.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED,
                report.developmentFixture(), summary.interpretation(), summary.jobLossDisclaimer(),
                summary.methodologyCaveat(), report.measures().stream().map(value ->
                    new ProfessionalReportViewModel.MeasureView(measureLabel(value.measure().name()),
                            String.format(Locale.ROOT, "%.2f", value.indicator()), label(value.band().name()),
                            String.format(Locale.ROOT, "%.0f%%", value.confidence() * 100.0),
                            value.conciseExplanation(), value.detailedExplanation().orElse(null))).toList(),
                report.exposureDrivers(), report.protectiveFactors(),
                new ProfessionalReportViewModel.NarrativeView(narrative.available(),
                        narrative.summary().orElse(null), narrative.whatThisMeans().orElse(null),
                        narrative.exposureExplanation().orElse(null),
                        narrative.humanAdvantageExplanation().orElse(null),
                        narrative.complementarityExplanation().orElse(null),
                        narrative.adaptabilityExplanation().orElse(null),
                        narrative.cautionOrLimitations().orElse(null)),
                report.practicalActions(), report.skillPriorities().stream().map(value ->
                    new ProfessionalReportViewModel.SkillView(label(value.priority().name()),
                            value.title(), value.rationale())).toList(),
                report.workFocus().areas().stream().map(value ->
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
                date, metric, item.relevanceNote());
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
