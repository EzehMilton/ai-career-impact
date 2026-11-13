package com.chikere.futureai.judgment.openai.university;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
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
public class UniversityOpenAiAutoConfiguration {
    @Bean UniversityJudgmentSchema openAiUniversityJudgmentSchema() { return new UniversityJudgmentSchema(); }
    @Bean UniversityJevStateMapper openAiUniversityStateMapper() { return new UniversityJevStateMapper(); }

    @Bean @ConditionalOnBean(DecisionModel.class)
    UniversityOpenAiJudgmentService universityOpenAiJudgmentService(DecisionModel decisionModel,
            UniversityJudgmentSchema schema, UniversityJevStateMapper mapper,
            @Value("${spring.ai.openai.decision.model:gpt-6-luna}") String model,
            ObjectProvider<ProviderCostTracker> costs, ObjectProvider<AssessmentTrace> trace) {
        return new UniversityOpenAiJudgmentService(decisionModel, schema, mapper, model,
                costs.getIfAvailable(ProviderCostTracker::disabled), trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
