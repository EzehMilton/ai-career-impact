package com.chikere.futureai.responsibility.professional.typesafe;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * The real Jev responsibility provider, in the same profiles as the real role-level Jev adapter. It is called only
 * by the paid responsibility-analysis lifecycle, never while rendering.
 */
@AutoConfiguration(after = TypeSafeAutoConfiguration.class)
@ConditionalOnClass(TypeSafeClient.class)
@ConditionalOnProperty(name = "app.judgment.provider", havingValue = "jev")
@Profile({"prod", "live-smoke"})
public class ProfessionalResponsibilityJevAutoConfiguration {

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    ProfessionalResponsibilityJevProvider professionalResponsibilityJevProvider(TypeSafeClient typeSafeClient,
            @Value("${spring.ai.typesafe.model:}") String configuredModelIdentifier,
            ObjectProvider<ProviderCostTracker> costs, ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalResponsibilityJevProvider(typeSafeClient, new ProfessionalResponsibilitySchema(),
                new ProfessionalResponsibilityStateMapper(), configuredModelIdentifier,
                costs.getIfAvailable(ProviderCostTracker::disabled), trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
