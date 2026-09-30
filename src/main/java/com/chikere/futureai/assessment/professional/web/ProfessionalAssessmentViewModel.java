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
        NarrativeView narrative) {

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
}
