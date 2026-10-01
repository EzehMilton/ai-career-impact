package com.chikere.futureai.report.university.web;

import java.util.List;

public record UniversityReportViewModel(
        String courseTitle,
        String institution,
        String headline,
        String status,
        String confidence,
        boolean moreInformationRequired,
        boolean developmentFixture,
        String interpretation,
        String employmentDisclaimer,
        String methodologyCaveat,
        List<MeasureView> measures,
        List<String> keyStrengths,
        List<String> developmentAreas,
        NarrativeView narrative,
        List<String> practicalActions,
        List<SkillView> skillPriorities,
        CareerAreaView careerAreas,
        EvidenceView evidence,
        MethodologyView methodology) {

    public record MeasureView(String name, String indicator, String band, String confidence,
            String conciseExplanation, String detailedExplanation) { }

    public record NarrativeView(boolean available, String summary, String whatThisMeans,
            String futureReadinessExplanation, String strengthsExplanation,
            String developmentOpportunitiesExplanation, String complementarityExplanation,
            String transferableSkillsExplanation, String careerAdaptabilityExplanation, String caution) { }

    public record SkillView(String priority, String title, String rationale) { }

    public record CareerAreaView(List<String> areasToExplore, String guidance, String limitation) { }

    public record EvidenceView(String status, String message, List<EvidenceItemView> items) { }

    public record EvidenceItemView(String category, String title, String summary, String source,
            String sourceUrl, String geography, String date, String metric, String relevance) { }

    public record MethodologyView(String judgmentSchemaVersion, String decisionPolicyVersion,
            String narrativeVersion, String evidenceVersion, String reportVersion,
            List<String> limitations) { }
}
