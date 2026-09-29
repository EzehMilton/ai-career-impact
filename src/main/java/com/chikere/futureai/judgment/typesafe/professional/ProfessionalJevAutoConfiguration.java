package com.chikere.futureai.judgment.typesafe.professional;

import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@AutoConfiguration(after = TypeSafeAutoConfiguration.class)
@ConditionalOnClass(TypeSafeClient.class)
@Profile("!local-fixture")
public class ProfessionalJevAutoConfiguration {

    @Bean
    ProfessionalJudgmentSchema professionalJudgmentSchema() {
        return new ProfessionalJudgmentSchema();
    }

    @Bean
    ProfessionalJevStateMapper professionalJevStateMapper() {
        return new ProfessionalJevStateMapper();
    }

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    ProfessionalJevJudgmentService professionalJevJudgmentService(
            TypeSafeClient typeSafeClient,
            ProfessionalJudgmentSchema schema,
            ProfessionalJevStateMapper stateMapper) {
        return new ProfessionalJevJudgmentService(typeSafeClient, schema, stateMapper);
    }
}
