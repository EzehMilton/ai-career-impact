package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProfessionalAssessmentConfiguration {

    @Bean
    ProfessionalDecisionPolicyV1 professionalDecisionPolicyV1() {
        return new ProfessionalDecisionPolicyV1();
    }

    @Bean
    ProfessionalAssessmentService professionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy) {
        return new ProfessionalAssessmentService(judgmentProvider, decisionPolicy);
    }
}
