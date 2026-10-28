package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/**
 * The paid University module-analysis lifecycle. The service exists in every profile; where no module provider is
 * configured (dev, ordinary tests) generation reports PROVIDER_NOT_CONFIGURED instead of inventing judgments. Nothing
 * calls it yet: the paid University report flow will.
 */
@Configuration(proxyBeanMethods = false)
public class UniversityModuleAnalysisConfiguration {

    @Bean
    UniversityModuleAnalysisService universityModuleAnalysisService(UniversityAssessmentStore assessments,
            UniversityReportPurchaseUseCase purchases, ObjectProvider<UniversityModuleJudgmentProvider> provider,
            UniversityModuleAnalysisStore store, ObjectProvider<AssessmentTrace> trace) {
        return new UniversityModuleAnalysisService(assessments, purchases, () -> Optional.ofNullable(provider.getIfAvailable()),
                UniversityModuleClassifier.v1(new UniversityModulePolicyV1()), store, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
