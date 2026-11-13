package com.chikere.futureai.decision.openai.poc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiDecisionOptions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit one-call live probe. Normal test and verify commands never select this class. */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "openai.decision.poc.live.enabled", matches = "true")
class OpenAiDecisionProofOfConceptLiveIT {

    @Test
    void callsOpenAiDecisionsWithTheRepresentativeQuestions() {
        OpenAiDecisionOptions.Builder options = OpenAiDecisionOptions.builder();
        options.apiKey(System.getenv("OPENAI_API_KEY"));
        options.model("gpt-6-luna");
        options.timeout(Duration.ofSeconds(60));
        options.maxRetries(0);
        var model = OpenAiDecisionModel.builder().options(options.build()).build();

        var result = new OpenAiDecisionProofOfConcept(model).evaluate(new OpenAiDecisionProofOfConceptInput(
                "Senior software architect",
                "Designs regulated financial systems, reviews production changes and explains trade-offs to teams.",
                "Approve production architecture changes that can affect customer financial data",
                List.of("Architecture", "Risk analysis", "Stakeholder communication")));

        assertThat(result.model()).startsWith("gpt-6-luna");
        assertThat(result.informationSufficient().probabilityOfYes()).isBetween(0.0, 1.0);
        assertThat(result.taskRepeatability().expectedLevel()).isBetween(0.0, 4.0);
        assertThat(result.outcomeAccountability().expectedLevel()).isBetween(0.0, 4.0);
        System.out.println("OpenAI Decisions Phase 1 POC result: " + result);
    }
}
