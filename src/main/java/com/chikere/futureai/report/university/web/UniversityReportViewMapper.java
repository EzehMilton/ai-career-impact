package com.chikere.futureai.report.university.web;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.report.university.application.UniversityReport;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class UniversityReportViewMapper {

    public UniversityReportViewModel toView(UniversityReport report) {
        var summary = report.assessmentSummary();
        var narrative = report.narrative();
        var evidence = report.evidence();
        var careers = report.careerAreas();
        return new UniversityReportViewModel(report.courseTitle(), report.institution().orElse(null),
                report.headline(), label(summary.status().name()), label(summary.confidence().name()),
                summary.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED,
                report.developmentFixture(), summary.interpretation(), summary.employmentDisclaimer(),
                summary.methodologyCaveat(),
                report.measures().stream().map(value ->
                        new UniversityReportViewModel.MeasureView(measureLabel(value.measure().name()),
                                String.format(Locale.ROOT, "%.2f", value.indicator()), label(value.band().name()),
                                String.format(Locale.ROOT, "%.0f%%", value.confidence() * 100.0),
                                value.conciseExplanation(), value.detailedExplanation().orElse(null))).toList(),
                report.keyStrengths(), report.developmentAreas(),
                new UniversityReportViewModel.NarrativeView(narrative.available(),
                        narrative.summary().orElse(null), narrative.whatThisMeans().orElse(null),
                        narrative.futureReadinessExplanation().orElse(null),
                        narrative.strengthsExplanation().orElse(null),
                        narrative.developmentOpportunitiesExplanation().orElse(null),
                        narrative.complementarityExplanation().orElse(null),
                        narrative.transferableSkillsExplanation().orElse(null),
                        narrative.careerAdaptabilityExplanation().orElse(null),
                        narrative.cautionOrLimitations().orElse(null)),
                report.practicalActions(),
                report.skillPriorities().stream().map(value ->
                        new UniversityReportViewModel.SkillView(label(value.priority().name()),
                                value.title(), value.rationale())).toList(),
                new UniversityReportViewModel.CareerAreaView(careers.areasToExplore(), careers.guidance(),
                        careers.limitation()),
                new UniversityReportViewModel.EvidenceView(label(evidence.status().name()), evidence.message(),
                        evidence.items().stream().map(this::evidenceItem).toList()),
                new UniversityReportViewModel.MethodologyView(
                        report.methodology().judgmentSchemaVersion(),
                        report.methodology().decisionPolicyVersion(),
                        report.methodology().narrativeVersion().orElse(null),
                        report.methodology().evidenceVersion().orElse(null),
                        report.methodology().reportVersion(), report.methodology().limitations()));
    }

    private UniversityReportViewModel.EvidenceItemView evidenceItem(UniversityReport.EvidenceItem item) {
        String source = item.publisher().map(value -> item.sourceName() + " · " + value).orElse(item.sourceName());
        String date = item.publicationDate().map(Object::toString)
                .orElse("Retrieved " + item.retrievedAt().toString().substring(0, 10));
        String metric = item.metricValue().map(value -> item.metricName().orElseThrow() + ": "
                + value.stripTrailingZeros().toPlainString() + " " + item.metricUnit().orElseThrow()).orElse(null);
        return new UniversityReportViewModel.EvidenceItemView(label(item.category()), item.title(), item.summary(),
                source, item.sourceUrl().map(Object::toString).orElse(null), item.geography().orElse(null),
                date, metric, item.relevanceNote());
    }

    private String measureLabel(String name) {
        return switch (name) {
            case "FUTURE_READINESS" -> "Future Readiness";
            case "AI_COMPLEMENTARITY" -> "AI Complementarity";
            case "HUMAN_DIFFERENTIATION" -> "Human Differentiation";
            case "SKILL_TRANSFERABILITY" -> "Skill Transferability";
            case "CAREER_ADAPTABILITY" -> "Career Adaptability";
            default -> label(name);
        };
    }

    private String label(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
