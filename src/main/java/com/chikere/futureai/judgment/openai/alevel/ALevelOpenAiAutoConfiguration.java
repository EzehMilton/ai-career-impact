package com.chikere.futureai.judgment.openai.alevel;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevStateMapper;
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
public class ALevelOpenAiAutoConfiguration {
    @Bean ALevelJudgmentSchemaV2 openAiALevelJudgmentSchemaV2() { return new ALevelJudgmentSchemaV2(); }
    @Bean ALevelV2JevStateMapper openAiALevelStateMapper() { return new ALevelV2JevStateMapper(); }

    @Bean @ConditionalOnBean(DecisionModel.class)
    ALevelV2OpenAiJudgmentService aLevelV2OpenAiJudgmentService(DecisionModel decisionModel,
            ALevelJudgmentSchemaV2 schema, ALevelV2JevStateMapper mapper,
            @Value("${spring.ai.openai.decision.model:gpt-6-luna}") String model,
            ObjectProvider<ProviderCostTracker> costs, ObjectProvider<AssessmentTrace> trace) {
        return new ALevelV2OpenAiJudgmentService(decisionModel, schema, mapper, model,
                costs.getIfAvailable(ProviderCostTracker::disabled), trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
