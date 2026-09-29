package com.chikere.futureai.assessment.domain;

public sealed interface AssessmentState
        permits ProfessionalAssessmentState, UniversityAssessmentState, ALevelAssessmentState {

    JourneyType journey();
}
