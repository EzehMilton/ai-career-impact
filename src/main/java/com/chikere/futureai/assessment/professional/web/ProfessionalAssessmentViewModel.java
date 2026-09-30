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
        List<String> protectiveFactors) {

    public record MeasureView(
            String name,
            String indicator,
            String band,
            String confidence,
            String explanation) {
    }
}
