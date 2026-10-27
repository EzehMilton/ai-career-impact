package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/**
 * The paid-narrative lifecycle exists in every profile. Without a configured provider (dev, ordinary tests) it
 * records a deterministic FALLBACK and calls nothing.
 */
@Configuration(proxyBeanMethods = false)
public class ProfessionalPaidNarrativeConfiguration {

    @Bean
    ProfessionalPaidNarrativeService professionalPaidNarrativeService(ProfessionalPaidNarrativeStore store,
            ObjectProvider<ProfessionalPaidNarrativeGenerator> generator, ObjectProvider<AssessmentTrace> trace) {
        AssessmentTrace assessmentTrace = trace.getIfAvailable(AssessmentTrace::disabled);
        return new ProfessionalPaidNarrativeService(store, () -> Optional.ofNullable(generator.getIfAvailable()),
                new ProfessionalPaidNarrativeValidator(assessmentTrace), Clock.systemUTC(), assessmentTrace);
    }
}
