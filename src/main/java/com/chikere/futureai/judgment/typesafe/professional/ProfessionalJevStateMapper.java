package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;

import java.util.Map;

public final class ProfessionalJevStateMapper {

    private final com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper delegate =
            new com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper();

    public Map<String, Object> map(ProfessionalAssessmentState assessmentState) {
        return delegate.map(assessmentState);
    }
}
