package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.UUID;

public interface ALevelAssessmentSubmission {
    UUID submitALevelAssessment(ALevelAssessmentState state);
}
