package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;

import java.util.UUID;

public interface UniversityAssessmentSubmission {
    UUID submitUniversityAssessment(UniversityAssessmentState state);
}
