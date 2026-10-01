package com.chikere.futureai.product.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantee that development chrome is a property of the running profile and nothing else.
 *
 * <p>This is checked here rather than by starting the application without the fixture profile,
 * because the non-fixture profiles deliberately require real provider credentials and therefore
 * cannot be started offline — there is no fixture fallback by design.</p>
 */
class ProductShellConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductShellConfiguration.class);

    @Test
    void theFixtureProfileDescribesTheFixturesInUse() {
        runner.withPropertyValues("spring.profiles.active=local-fixture",
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
    void anyOtherProfileHasNoDevelopmentModeAtAll() {
        for (String profiles : new String[] {"", "live-evidence", "live-payment",
                "live-evidence,live-payment"}) {
            runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
                DevelopmentModeStatus status = context.getBean(DevelopmentModeStatus.class);
                assertThat(status.active()).as("profiles=%s", profiles).isFalse();
                assertThat(status.fixtureSummary()).as("profiles=%s", profiles).isEmpty();
            });
        }
    }

    @Test
    void developmentModeCannotBeTurnedOnByAProperty() {
        // Nothing outside the profile may enable it: not a request, a header, or a stray property.
        runner.withPropertyValues("spring.profiles.active=live-payment",
                        "app.development-mode=true", "developmentMode=true",
                        "app.professional.fixture-id=senior_software_architect")
                .run(context -> assertThat(context.getBean(DevelopmentModeStatus.class).active()).isFalse());
    }

    @Test
    void theFixtureProfileStillReportsDevelopmentModeWhenNoFixtureIdIsConfigured() {
        runner.withPropertyValues("spring.profiles.active=local-fixture").run(context -> {
            DevelopmentModeStatus status = context.getBean(DevelopmentModeStatus.class);
            assertThat(status.active()).isTrue();
        });
    }
}
