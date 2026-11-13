package com.chikere.futureai.responsibility.professional.openai;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityStateMapper;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration(after = org.springframework.ai.model.openai.autoconfigure.OpenAiDecisionAutoConfiguration.class)
@Profile({"prod", "live-smoke"})
@ConditionalOnProperty(name = "app.judgment.provider", havingValue = "openai", matchIfMissing = true)
public class ProfessionalResponsibilityOpenAiAutoConfiguration {
    @Bean @ConditionalOnBean(DecisionModel.class)
    ProfessionalResponsibilityOpenAiProvider professionalResponsibilityOpenAiProvider(DecisionModel model,
            @Value("${spring.ai.openai.decision.model:gpt-6-luna}") String configuredModel,
            ObjectProvider<ProviderCostTracker> costs) {
        return new ProfessionalResponsibilityOpenAiProvider(model, new ProfessionalResponsibilitySchema(),
                new ProfessionalResponsibilityStateMapper(), configuredModel,
                costs.getIfAvailable(ProviderCostTracker::disabled));
    }
}
