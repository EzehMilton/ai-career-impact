package com.chikere.futureai.report.professional.web;

import java.util.List;

public record ProfessionalReportViewModel(
        String profession,
        String headline,
        String status,
        String confidence,
        boolean moreInformationRequired,
        boolean developmentFixture,
        String interpretation,
        String jobLossDisclaimer,
        String methodologyCaveat,
        List<MeasureView> measures,
        List<String> exposureDrivers,
        List<String> protectiveFactors,
        NarrativeView narrative,
        List<String> practicalActions,
        List<SkillView> skillPriorities,
        List<WorkFocusView> workFocus,
        String workFocusLimitation,
        EvidenceView evidence,
        ClassificationView classification,
        MethodologyView methodology) {

    public record MeasureView(String name, String indicator, String band, String confidence,
            String conciseExplanation, String detailedExplanation) { }

    public record NarrativeView(boolean available, String summary, String whatThisMeans,
            String exposureExplanation, String humanAdvantageExplanation,
            String complementarityExplanation, String adaptabilityExplanation, String caution) { }

    public record SkillView(String priority, String title, String rationale) { }

    public record WorkFocusView(String category, String title, String guidance,
            List<String> relatedResponsibilities) { }

    public record EvidenceView(String status, String message, List<EvidenceItemView> items) { }

    public record EvidenceItemView(String category, String title, String summary, String source,
            String sourceUrl, String geography, String date, String metric, String relevance) { }

    public record ClassificationView(String canonicalOccupation, String system, String code,
            String quality, String classifiedAt) { }

    public record MethodologyView(String judgmentSchemaVersion, String decisionPolicyVersion,
            String narrativeVersion, String evidenceVersion, String classifierVersion,
            String reportVersion, List<String> limitations) { }
}
