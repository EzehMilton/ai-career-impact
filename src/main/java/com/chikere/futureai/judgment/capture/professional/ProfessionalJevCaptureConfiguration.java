package com.chikere.futureai.judgment.capture.professional;

import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Wraps whichever professional judgment provider is active so each call is captured to a JSON file.
 * Registered only under the explicit {@code jev-capture} profile, which the startup validator refuses
 * to combine with {@code prod}.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "dev-no-jev"})
@ConditionalOnProperty(name = "app.dev.capture-jev", havingValue = "true")
public class ProfessionalJevCaptureConfiguration {
    static final String DIRECTORY_PROPERTY = "app.professional.jev-capture.directory";
    static final String DEFAULT_DIRECTORY = "target/jev-capture";

    @Bean
    static BeanPostProcessor professionalJevCapturePostProcessor(Environment environment,
            ObjectProvider<Clock> clock) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ProfessionalJudgmentProvider provider
                        && !(bean instanceof CapturingProfessionalJudgmentProvider)) {
                    return new CapturingProfessionalJudgmentProvider(provider,
                            new ProfessionalJevCapture(new ProfessionalJevStateMapper(),
                                    new ProfessionalJudgmentSchema()),
                            Path.of(environment.getProperty(DIRECTORY_PROPERTY, DEFAULT_DIRECTORY)),
                            clock.getIfAvailable(Clock::systemUTC));
                }
                return bean;
            }
        };
    }
}
