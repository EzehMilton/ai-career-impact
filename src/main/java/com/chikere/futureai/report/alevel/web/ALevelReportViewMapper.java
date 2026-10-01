package com.chikere.futureai.report.alevel.web;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class ALevelReportViewMapper {

    public ALevelReportViewModel toView(ALevelReport report) {
        var summary = report.assessmentSummary();
        var narrative = report.narrative();
        var evidence = report.evidence();
        var areas = report.preferredAreas();
        return new ALevelReportViewModel(report.selectedSubjects(), report.predictedGrades(),
                report.headline(), label(summary.status().name()), label(summary.confidence().name()),
                summary.status() == AssessmentStatus.MORE_INFORMATION_REQUIRED,
                report.developmentFixture(), summary.interpretation(), summary.admissionsDisclaimer(),
                summary.methodologyCaveat(),
                report.measures().stream().map(value ->
                        new ALevelReportViewModel.MeasureView(measureLabel(value.measure().name()),
                                String.format(Locale.ROOT, "%.2f", value.indicator()), label(value.band().name()),
                                String.format(Locale.ROOT, "%.0f%%", value.confidence() * 100.0),
                                value.conciseExplanation(), value.detailedExplanation().orElse(null))).toList(),
                report.mainStrengths(), report.developmentOpportunities(),
                new ALevelReportViewModel.NarrativeView(narrative.available(),
                        narrative.summary().orElse(null), narrative.whatThisMeans().orElse(null),
                        narrative.futureOptionalityExplanation().orElse(null),
                        narrative.strengthsExplanation().orElse(null),
                        narrative.degreePathwayExplanation().orElse(null),
                        narrative.analyticalFoundationExplanation().orElse(null),
                        narrative.humanAndCreativeExplanation().orElse(null),
                        narrative.transferabilityExplanation().orElse(null),
                        narrative.adaptabilityExplanation().orElse(null),
                        narrative.developmentOpportunitiesExplanation().orElse(null),
                        narrative.cautionOrLimitations().orElse(null)),
                report.practicalActions(),
                report.guidance().stream().map(value ->
                        new ALevelReportViewModel.GuidanceView(label(value.category().name()),
                                value.title(), value.suggestion())).toList(),
                new ALevelReportViewModel.PreferredAreaView(areas.degreeAreas(), areas.careerAreas(),
                        areas.guidance(), areas.limitation()),
                new ALevelReportViewModel.EvidenceView(label(evidence.status().name()), evidence.message(),
                        evidence.separationNote(),
                        evidence.items().stream().map(this::evidenceItem).toList()),
                new ALevelReportViewModel.MethodologyView(
                        report.methodology().judgmentSchemaVersion(),
                        report.methodology().decisionPolicyVersion(),
                        report.methodology().narrativeVersion().orElse(null),
                        report.methodology().evidenceVersion().orElse(null),
                        report.methodology().reportVersion(), report.methodology().limitations()));
    }

    private ALevelReportViewModel.EvidenceItemView evidenceItem(ALevelReport.EvidenceItem item) {
        String source = item.publisher().map(value -> item.sourceName() + " · " + value).orElse(item.sourceName());
        String date = item.publicationDate().map(Object::toString)
                .orElse("Retrieved " + item.retrievedAt().toString().substring(0, 10));
        return new ALevelReportViewModel.EvidenceItemView(label(item.category()), item.title(),
                item.summary(), source, item.sourceUrl().map(Object::toString).orElse(null),
                item.geography().orElse(null), date, item.relevanceNote());
    }

    private String measureLabel(String name) {
        return switch (name) {
            case "FUTURE_OPTIONALITY" -> "Future Optionality";
            case "DEGREE_PATHWAY_BREADTH" -> "Degree Pathway Breadth";
            case "ANALYTICAL_FOUNDATION" -> "Analytical Foundation";
            case "HUMAN_AND_CREATIVE_STRENGTH" -> "Human and Creative Strength";
            case "SKILL_TRANSFERABILITY" -> "Skill Transferability";
            case "AI_ERA_ADAPTABILITY" -> "AI-Era Adaptability";
            default -> label(name);
        };
    }

    private String label(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
