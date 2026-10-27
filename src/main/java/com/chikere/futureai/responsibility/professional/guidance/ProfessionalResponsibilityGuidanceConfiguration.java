package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalResponsibilityGuidanceConfiguration {

    @Bean
    ProfessionalGuidancePolicyV1 professionalGuidancePolicyV1() {
        return new ProfessionalGuidancePolicyV1();
    }

    @Bean
    ProfessionalResponsibilityGuidanceService professionalResponsibilityGuidanceService(
            ProfessionalReportPurchaseUseCase purchases, ProfessionalResponsibilityAnalysisService analyses,
            ProfessionalGuidancePolicyV1 policy, ProfessionalResponsibilityGuidanceStore store,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalResponsibilityGuidanceService(purchases, analyses, policy, store, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
