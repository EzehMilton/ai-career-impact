package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

public interface ProfessionalJudgmentProvider {

    AssessmentJudgmentSet judge(ProfessionalAssessmentState state);

    AssessmentSource source();
}
