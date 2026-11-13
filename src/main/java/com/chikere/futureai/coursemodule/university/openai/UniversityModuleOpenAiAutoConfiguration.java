package com.chikere.futureai.coursemodule.university.openai;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleStateMapper;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration(after = org.springframework.ai.model.openai.autoconfigure.OpenAiDecisionAutoConfiguration.class)
@Profile({"prod", "live-smoke"})
@ConditionalOnProperty(name = "app.judgment.provider", havingValue = "openai", matchIfMissing = true)
public class UniversityModuleOpenAiAutoConfiguration {
    @Bean @ConditionalOnBean(DecisionModel.class)
    UniversityModuleOpenAiProvider universityModuleOpenAiProvider(DecisionModel model,
            ObjectProvider<ProviderCostTracker> costs) {
        return new UniversityModuleOpenAiProvider(model, new UniversityModuleSchema(),
                new UniversityModuleStateMapper(), costs.getIfAvailable(ProviderCostTracker::disabled));
    }
}
