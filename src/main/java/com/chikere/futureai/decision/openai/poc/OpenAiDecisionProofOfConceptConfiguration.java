package com.chikere.futureai.decision.openai.poc;

import org.springframework.ai.decision.DecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Enables only the Phase 1 proof of concept; production judgment providers remain unchanged. */
@Configuration(proxyBeanMethods = false)
@Profile("openai-decision-poc")
public class OpenAiDecisionProofOfConceptConfiguration {

    @Bean
    OpenAiDecisionProofOfConcept openAiDecisionProofOfConcept(DecisionModel decisionModel) {
        return new OpenAiDecisionProofOfConcept(decisionModel);
    }
}
