package com.chikere.futureai.product.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Product shell wiring.
 *
 * <p>Two mutually exclusive {@link DevelopmentModeStatus} beans are declared by profile. Nothing
 * reads a request to decide which applies, so the development chrome cannot leak into a normal
 * deployment.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ProductShellConfiguration {

    @Bean
    @Profile("dev")
    DevelopmentModeStatus localFixtureDevelopmentModeStatus(
            @Value("${app.professional.fixture-id:}") String professionalFixtureId,
            @Value("${app.university.fixture-id:}") String universityFixtureId,
            @Value("${app.a-level.fixture-id:}") String aLevelFixtureId) {
        return DevelopmentModeStatus.active("professional: " + describe(professionalFixtureId)
                + " · university: " + describe(universityFixtureId)
                + " · a-level: " + describe(aLevelFixtureId));
    }

    @Bean
    @Profile({"prod", "dev-no-jev"})
    DevelopmentModeStatus productionDevelopmentModeStatus() {
        return DevelopmentModeStatus.inactive();
    }

    private static String describe(String fixtureId) {
        return fixtureId == null || fixtureId.isBlank() ? "not set" : fixtureId.strip();
    }
}
