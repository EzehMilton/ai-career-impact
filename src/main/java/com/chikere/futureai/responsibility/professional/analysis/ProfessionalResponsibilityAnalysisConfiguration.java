package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/**
 * The paid responsibility-analysis lifecycle. The service exists in every profile; where no responsibility provider
 * is configured (dev, ordinary tests) generation reports PROVIDER_NOT_CONFIGURED instead of inventing judgments.
 */
@Configuration(proxyBeanMethods = false)
public class ProfessionalResponsibilityAnalysisConfiguration {

    @Bean
    ProfessionalResponsibilityPolicyV1 professionalResponsibilityPolicyV1(ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalResponsibilityPolicyV1(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalResponsibilityAnalysisService professionalResponsibilityAnalysisService(
            ProfessionalAssessmentStore assessments, ProfessionalReportPurchaseUseCase purchases,
            ObjectProvider<ProfessionalResponsibilityJudgmentProvider> provider, ProfessionalResponsibilityPolicyV1 policy,
            ProfessionalResponsibilityAnalysisStore store, ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalResponsibilityAnalysisService(assessments, purchases,
                () -> Optional.ofNullable(provider.getIfAvailable()), policy, store, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
