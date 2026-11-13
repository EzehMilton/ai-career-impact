package com.chikere.futureai.judgment.professional.schema;

/** Provider-independent definition of one professional-v1 atomic judgment. */
public sealed interface ProfessionalQuestion permits ProfessionalPredicateQuestion, ProfessionalScoreQuestion {

    String id();

    String instructions();
}
