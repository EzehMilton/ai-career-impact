package com.chikere.futureai.product.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantee that development chrome is a property of the running profile and nothing else.
 */
class ProductShellConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductShellConfiguration.class);

    @Test
    void theDevProfileDescribesTheFixturesInUse() {
        runner.withPropertyValues("spring.profiles.active=dev",
                        "app.professional.fixture-id=senior_software_architect",
                        "app.university.fixture-id=computer_science",
                        "app.a-level.fixture-id=maths_economics_geography")
                .run(context -> {
                    DevelopmentModeStatus status = context.getBean(DevelopmentModeStatus.class);
                    assertThat(status.active()).isTrue();
                    assertThat(status.fixtureSummary()).get().asString()
                            .contains("senior_software_architect", "computer_science",
                                    "maths_economics_geography");
                });
    }

    @Test
    void prodAndDevNoJevHaveNoDevelopmentModeAtAll() {
        for (String profile : new String[]{"prod", "dev-no-jev"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                DevelopmentModeStatus status = context.getBean(DevelopmentModeStatus.class);
                assertThat(status.active()).as("profile=%s", profile).isFalse();
                assertThat(status.fixtureSummary()).as("profile=%s", profile).isEmpty();
            });
        }
    }

    @Test
    void developmentModeCannotBeTurnedOnByAProperty() {
        runner.withPropertyValues("spring.profiles.active=prod",
                        "app.development-mode=true", "developmentMode=true",
                        "app.professional.fixture-id=senior_software_architect")
                .run(context -> assertThat(context.getBean(DevelopmentModeStatus.class).active()).isFalse());
    }

    @Test
    void theDevProfileStillReportsDevelopmentModeWhenNoFixtureIdIsConfigured() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            DevelopmentModeStatus status = context.getBean(DevelopmentModeStatus.class);
            assertThat(status.active()).isTrue();
        });
    }
}
