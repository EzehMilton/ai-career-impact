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
/**
 * Jev for new A-level assessments: the {@code a-level-v2} schema, its subject-only wire state and its adapter. The
 * historical {@code a-level-v1} adapter is not registered: stored v1 judgments are never requested again.
 */
public class ALevelJevAutoConfiguration {

    @Bean
    ALevelJudgmentSchemaV2 aLevelJudgmentSchemaV2() {
        return new ALevelJudgmentSchemaV2();
    }

    @Bean
    ALevelV2JevStateMapper aLevelV2JevStateMapper() {
        return new ALevelV2JevStateMapper();
    }

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    ALevelV2JevJudgmentService aLevelV2JevJudgmentService(TypeSafeClient typeSafeClient,
            ALevelJudgmentSchemaV2 schema, ALevelV2JevStateMapper stateMapper,
            @Value("${spring.ai.typesafe.model:}") String configuredModelIdentifier,
            ObjectProvider<ProviderCostTracker> costs,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelV2JevJudgmentService(typeSafeClient, schema, stateMapper, configuredModelIdentifier,
                costs.getIfAvailable(ProviderCostTracker::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
