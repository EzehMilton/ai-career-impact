package com.chikere.futureai.coursemodule.university.typesafe;

import com.chikere.futureai.cost.ProviderCostTracker;
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
 * The real Jev module provider, in the same profiles as the real course-level Jev adapter. It is called only by the
 * paid module-analysis lifecycle, never while rendering.
 */
@AutoConfiguration(after = TypeSafeAutoConfiguration.class)
@ConditionalOnClass(TypeSafeClient.class)
@ConditionalOnProperty(name = "app.judgment.provider", havingValue = "jev")
@Profile({"prod", "live-smoke"})
public class UniversityModuleJevAutoConfiguration {

    @Bean
    @ConditionalOnBean(TypeSafeClient.class)
    UniversityModuleJevProvider universityModuleJevProvider(TypeSafeClient typeSafeClient,
            @Value("${spring.ai.typesafe.model:}") String configuredModelIdentifier,
            ObjectProvider<ProviderCostTracker> costs) {
        return new UniversityModuleJevProvider(typeSafeClient, new UniversityModuleSchema(),
                new UniversityModuleStateMapper(), configuredModelIdentifier,
                costs.getIfAvailable(ProviderCostTracker::disabled));
    }
}
