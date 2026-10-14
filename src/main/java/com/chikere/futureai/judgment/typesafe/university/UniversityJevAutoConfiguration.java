package com.chikere.futureai.judgment.typesafe.university;

import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration(after = TypeSafeAutoConfiguration.class)
@ConditionalOnClass(TypeSafeClient.class)
@Profile("prod")
public class UniversityJevAutoConfiguration {

    @Bean
    UniversityJudgmentSchema universityJudgmentSchema() {
        return new UniversityJudgmentSchema();
    }

    @Bean
    UniversityJevStateMapper universityJevStateMapper() {
        return new UniversityJevStateMapper();
    }

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    UniversityJevJudgmentService universityJevJudgmentService(
            TypeSafeClient typeSafeClient,
            UniversityJudgmentSchema schema,
            UniversityJevStateMapper stateMapper,
            @Value("${spring.ai.typesafe.model:}") String configuredModelIdentifier) {
        return new UniversityJevJudgmentService(typeSafeClient, schema, stateMapper, configuredModelIdentifier);
    }
}
