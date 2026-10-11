package com.chikere.futureai.judgment.capture.professional;

import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalJevCaptureConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProfessionalJevCaptureConfiguration.class)
            .withBean(ProfessionalJudgmentProvider.class,
                    () -> new LocalFixtureProfessionalJudgmentProvider("electrician"));

    @Test
    void wrapsTheJudgmentProviderOnlyUnderTheJevCaptureProfile() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("jev-capture"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ProfessionalJudgmentProvider.class);
                    var provider = context.getBean(ProfessionalJudgmentProvider.class);
                    assertThat(provider).isInstanceOf(CapturingProfessionalJudgmentProvider.class);
                    assertThat(((CapturingProfessionalJudgmentProvider) provider).delegate())
                            .isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
                });
    }

    @Test
    void leavesTheJudgmentProviderAloneWithoutTheProfile() {
        contextRunner.run(context -> assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                .isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class));
    }
}
