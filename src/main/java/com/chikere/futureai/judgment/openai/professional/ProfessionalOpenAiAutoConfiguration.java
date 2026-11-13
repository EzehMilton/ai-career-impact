package com.chikere.futureai.judgment.openai.professional;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper;
import com.chikere.futureai.trace.AssessmentTrace;
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
public class ProfessionalOpenAiAutoConfiguration {

    @Bean
    ProfessionalJudgmentSchema canonicalProfessionalJudgmentSchema() {
        return new ProfessionalJudgmentSchema();
    }

    @Bean
    ProfessionalJudgmentStateMapper canonicalProfessionalJudgmentStateMapper() {
        return new ProfessionalJudgmentStateMapper();
    }

    @Bean
    @ConditionalOnBean(DecisionModel.class)
    ProfessionalOpenAiJudgmentService professionalOpenAiJudgmentService(DecisionModel decisionModel,
            ProfessionalJudgmentSchema schema, ProfessionalJudgmentStateMapper stateMapper,
            @Value("${spring.ai.openai.decision.model:gpt-6-luna}") String model,
            ObjectProvider<ProviderCostTracker> costs, ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalOpenAiJudgmentService(decisionModel, schema, stateMapper, model,
                costs.getIfAvailable(ProviderCostTracker::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
