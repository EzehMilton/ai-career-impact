package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration(after = TypeSafeAutoConfiguration.class)
@ConditionalOnClass(TypeSafeClient.class)
@Profile({"prod", "live-smoke"})
public class ALevelJevAutoConfiguration {

    @Bean
    ALevelJudgmentSchema aLevelJudgmentSchema() {
        return new ALevelJudgmentSchema();
    }

    @Bean
    ALevelJevStateMapper aLevelJevStateMapper() {
        return new ALevelJevStateMapper();
    }

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    ALevelJevJudgmentService aLevelJevJudgmentService(TypeSafeClient typeSafeClient,
            ALevelJudgmentSchema schema, ALevelJevStateMapper stateMapper,
            @Value("${spring.ai.typesafe.model:}") String configuredModelIdentifier,
            ObjectProvider<ProviderCostTracker> costs,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelJevJudgmentService(typeSafeClient, schema, stateMapper, configuredModelIdentifier,
                costs.getIfAvailable(ProviderCostTracker::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
