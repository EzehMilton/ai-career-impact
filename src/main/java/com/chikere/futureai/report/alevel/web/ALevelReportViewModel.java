package com.chikere.futureai.report.alevel.web;

import java.util.List;
import java.util.Map;

public record ALevelReportViewModel(
        List<String> selectedSubjects,
        Map<String, String> predictedGrades,
        String headline,
        String status,
        String confidence,
        boolean moreInformationRequired,
        boolean developmentFixture,
        String interpretation,
        String admissionsDisclaimer,
        String methodologyCaveat,
        List<MeasureView> measures,
        List<String> mainStrengths,
        List<String> developmentOpportunities,
        NarrativeView narrative,
        List<String> practicalActions,
        List<GuidanceView> guidance,
        PreferredAreaView preferredAreas,
        EvidenceView evidence,
        MethodologyView methodology) {

    public record MeasureView(String name, String indicator, String band, String confidence,
            String conciseExplanation, String detailedExplanation) { }

    public record NarrativeView(boolean available, String summary, String whatThisMeans,
            String futureOptionalityExplanation, String strengthsExplanation,
            String degreePathwayExplanation, String analyticalFoundationExplanation,
            String humanAndCreativeExplanation, String transferabilityExplanation,
            String adaptabilityExplanation, String developmentOpportunitiesExplanation,
            String caution) { }

    public record GuidanceView(String category, String title, String suggestion) { }

    public record PreferredAreaView(List<String> degreeAreas, List<String> careerAreas,
            String guidance, String limitation) { }

    public record EvidenceView(String status, String message, String separationNote,
            List<EvidenceItemView> items) { }

    public record EvidenceItemView(String category, String title, String summary, String source,
            String sourceUrl, String geography, String date, String relevance) { }

    public record MethodologyView(String judgmentSchemaVersion, String decisionPolicyVersion,
            String narrativeVersion, String evidenceVersion, String reportVersion,
            List<String> limitations) { }
}
