package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalConfiguration;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProfessionalProviderWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ProfessionalJevAutoConfiguration.class))
            .withUserConfiguration(
                    ProfessionalAssessmentConfiguration.class,
                    LocalFixtureProfessionalConfiguration.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class));

    @Test
    void normalModeSelectsOnlyTheJevAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ProfessionalJudgmentProvider.class)).hasSize(1);
            assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                    .isInstanceOf(ProfessionalJevJudgmentService.class);
            assertThat(context.getBean(ProfessionalJudgmentProvider.class).source())
                    .isEqualTo(AssessmentSource.JEV);
            assertThat(context).hasSingleBean(ProfessionalAssessmentService.class);
            assertThat(context).doesNotHaveBean(LocalFixtureProfessionalJudgmentProvider.class);
        });
    }

    @Test
    void localFixtureModeSelectsOnlyFixtureEvenWhenATypeSafeClientExists() {
        contextRunner.withPropertyValues("app.professional.fixture-id=senior_software_architect")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ProfessionalJudgmentProvider.class)).hasSize(1);
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                            .isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class).source())
                            .isEqualTo(AssessmentSource.LOCAL_FIXTURE);
                    var result = context.getBean(ProfessionalAssessmentService.class).assess(
                            new ProfessionalAssessmentState("Software Architect", "Designs complex systems",
                                    List.of("Design systems"), List.of("Architecture"), OptionalInt.empty()));
                    assertThat(result.decision().measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().indicator())
                            .isBetween(4.80, 4.82);
                    assertThat(context).hasSingleBean(ProfessionalAssessmentService.class);
                    assertThat(context).doesNotHaveBean(ProfessionalJevJudgmentService.class);
                });
    }

    @Test
    void localFixtureModeFailsClearlyWithoutAnExplicitSupportedId() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("app.professional.fixture-id"));
        contextRunner.withPropertyValues("app.professional.fixture-id=unknown")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Unsupported app.professional.fixture-id"));
    }
}
