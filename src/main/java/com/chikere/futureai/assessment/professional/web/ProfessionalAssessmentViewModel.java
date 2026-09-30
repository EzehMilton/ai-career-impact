package com.chikere.futureai.assessment.professional.web;

import java.util.List;

public record ProfessionalAssessmentViewModel(
        String profession,
        String status,
        String confidence,
        boolean moreInformationRequired,
        boolean localFixture,
        List<MeasureView> measures,
        List<String> exposureDrivers,
        List<String> protectiveFactors,
        boolean narrativeUnavailable,
        NarrativeView narrative,
        boolean evidenceUnavailable,
        boolean evidencePartial,
        boolean fixtureEvidence,
        List<EvidenceView> evidenceItems) {

    public record MeasureView(
            String name,
            String indicator,
            String band,
            String confidence,
            String explanation) {
    }

    public record NarrativeView(
            String headline,
            String summary,
            String whatThisMeans,
            String exposureExplanation,
            String humanAdvantageExplanation,
            String complementarityExplanation,
            String adaptabilityExplanation,
            List<String> practicalActions,
            String cautionOrLimitations) {
    }

    public record EvidenceView(
            String category,
            String title,
            String summary,
            String source,
            String geography,
            String date,
            String metric,
            String sourceUrl,
            String relevanceNote) {
    }
}
