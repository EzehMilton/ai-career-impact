package com.chikere.futureai.narrative.openai;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContext;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeContext;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.openai.OpenAiALevelPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.paid.openai.OpenAiALevelPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContext;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.openai.OpenAiUniversityPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.university.paid.openai.OpenAiUniversityPaidNarrativeGenerator;
import com.chikere.futureai.report.professional.detailed.DetailedReportFixtures;
import com.chikere.futureai.report.university.detailed.UniversityCalibrationChains;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every OpenAI narrative request names its journey's model, whatever Spring AI's global chat model is. The real
 * configurations and Spring AI's OpenAI client run against a local HTTP stub that records each request body and
 * refuses it: no OpenAI call is ever made, and every attempt, retries included, is checked.
 */
class JourneyNarrativeModelRequestTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    /** Deliberately a legacy model: no journey request may inherit it. */
    private static final String GLOBAL_MODEL = "gpt-4o-mini";

    private HttpServer stub;
    private final List<JsonNode> bodies = new ArrayList<>();

    @BeforeEach
    void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            synchronized (bodies) {
                bodies.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            }
            byte[] refusal = "{\"error\":{\"message\":\"stub\",\"type\":\"invalid_request_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, refusal.length);
            exchange.getResponseBody().write(refusal);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    @Test
    void universityNarrativesFreeAndPaidSendGpt6LunaOnEveryCallIncludingTheRepair() {
        run(context -> {
            var free = context.getBean(OpenAiUniversityNarrativeGenerator.class);
            assertThat(free.modelIdentifier()).contains("gpt-6-luna");
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> free.generate(universityContext())));

            var paid = context.getBean(OpenAiUniversityPaidNarrativeGenerator.class);
            var paidContext = UniversityCalibrationChains.chain(UniversityCalibrationChains.NURSING).context();
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> paid.generate(paidContext)));
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> paid.repair(paidContext,
                    new UniversityPaidNarrativeGenerator.RepairFeedback("RULE", "why", "text"))));
        });
    }

    @Test
    void aLevelNarrativesFreeAndPaidSendGpt6LunaOnEveryCallIncludingTheRepair() {
        run(context -> {
            var free = context.getBean(OpenAiALevelNarrativeGenerator.class);
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> free.generate(aLevelContext())));

            var paid = context.getBean(OpenAiALevelPaidNarrativeGenerator.class);
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> paid.generate(aLevelPaidContext())));
            assertSent("gpt-6-luna", () -> assertThatThrownBy(() -> paid.repair(aLevelPaidContext(),
                    new ALevelPaidNarrativeGenerator.RepairFeedback("RULE", "why", "text"))));
        });
    }

    @Test
    void professionalNarrativesFreeAndPaidSendGpt61SolOnEveryCallIncludingTheRepair() {
        var feedback = new NarrativeRepairFeedback("RULE", "why", "text");
        run(context -> {
            var free = context.getBean(OpenAiProfessionalNarrativeGenerator.class);
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> free.generate(professionalContext())));
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> free.repair(professionalContext(), feedback)));

            var paid = context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class);
            var paidContext = DetailedReportFixtures.chain("solicitor-8").context();
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> paid.generate(paidContext)));
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> paid.repair(paidContext, feedback)));
        });
    }

    @Test
    void aDeliberateUpgradeIsSentAsConfigured() {
        run(context -> {
            var university = context.getBean(OpenAiUniversityPaidNarrativeGenerator.class);
            var paidContext = UniversityCalibrationChains.chain(UniversityCalibrationChains.NURSING).context();
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> university.generate(paidContext)));
            var aLevel = context.getBean(OpenAiALevelNarrativeGenerator.class);
            assertSent("gpt-6.1-sol", () -> assertThatThrownBy(() -> aLevel.generate(aLevelContext())));
            var professional = context.getBean(OpenAiProfessionalNarrativeGenerator.class);
            assertSent("gpt-6-astra", () -> assertThatThrownBy(() -> professional.generate(professionalContext())));
        }, "app.narrative.university.model=gpt-6.1-sol", "app.narrative.a-level.model=gpt-6.1-sol",
                "app.narrative.professional.model=gpt-6-astra");
    }

    @Test
    void aModelBelowTheJourneysTierPreventsTheProviderBeingCreated() {
        runner("app.narrative.professional.model=gpt-6-luna").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("Professional narrative model 'gpt-6-luna' is below "
                        + "the application's allowed Professional model tier."));
        runner("app.narrative.university.model=gpt-4o-mini").run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("University narrative model 'gpt-4o-mini' is below"));
        assertThat(bodies).isEmpty();
    }

    /**
     * Runs one call and checks every request it sent, retries included, against the stub's refusals: each named the
     * journey's model, a GPT-6 one carried reasoning effort and no custom temperature, and no attempt fell back to a
     * weaker model after a failure.
     */
    private void assertSent(String model, Runnable call) {
        synchronized (bodies) {
            bodies.clear();
        }
        call.run();
        assertThat(bodies).isNotEmpty().allSatisfy(body -> {
            assertThat(body.path("model").asText()).isEqualTo(model);
            assertThat(body.has("temperature")).as("GPT-6 requests carry no custom temperature").isFalse();
            assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        });
    }

    private void run(Consumer<AssertableApplicationContext> use, String... properties) {
        runner(properties).run(context -> {
            assertThat(context).hasNotFailed();
            use.accept(context);
        });
    }

    private ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class,
                        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
                        org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class))
                .withUserConfiguration(OpenAiUniversityNarrativeConfiguration.class,
                        OpenAiUniversityPaidNarrativeConfiguration.class, OpenAiALevelNarrativeConfiguration.class,
                        OpenAiALevelPaidNarrativeConfiguration.class, OpenAiProfessionalNarrativeConfiguration.class,
                        OpenAiProfessionalPaidNarrativeConfiguration.class)
                .withBean(ObjectMapper.class, () -> JSON)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.api-key=stub-not-a-key",
                        "spring.ai.openai.base-url=http://127.0.0.1:" + stub.getAddress().getPort() + "/v1",
                        "spring.ai.openai.chat.model=" + GLOBAL_MODEL)
                .withPropertyValues(properties);
    }

    private static UniversityNarrativeContext universityContext() {
        return new UniversityNarrativeContext("Nursing", "Clinical practice and placements.",
                List.of("Adult nursing"), List.of("Patient communication"), List.of("Adult nursing"),
                Optional.of("A UK university"), Optional.of("United Kingdom"),
                AssessmentStatus.MORE_INFORMATION_REQUIRED, AssessmentConfidence.LOW, List.of(), List.of(), List.of(),
                com.chikere.futureai.assessment.university.application.AssessmentSource.LOCAL_FIXTURE,
                "university-v1", "university-decision-v1");
    }

    private static ALevelNarrativeContext aLevelContext() {
        return new ALevelNarrativeContext(List.of("Mathematics", "Physics"), Map.of("Mathematics", "A"),
                List.of("Problem solving"), List.of("Engineering"), List.of(), Optional.of("United Kingdom"),
                Optional.empty(), AssessmentStatus.MORE_INFORMATION_REQUIRED, AssessmentConfidence.LOW, List.of(),
                List.of(), List.of(), com.chikere.futureai.assessment.alevel.application.AssessmentSource.LOCAL_FIXTURE,
                "a-level-v1", "a-level-decision-v1");
    }

    private static ALevelPaidNarrativeContext aLevelPaidContext() {
        return new ALevelPaidNarrativeContext(UUID.nameUUIDFromBytes("a-level".getBytes()),
                List.of("Mathematics", "Economics", "Geography"),
                List.of(new ALevelPaidNarrativeContext.Measure("Options Kept Open", "Broad", List.of(), List.of()),
                        new ALevelPaidNarrativeContext.Measure("Learning Alongside AI", "High", List.of(), List.of())),
                List.of("Mathematical and statistical reasoning"), List.of(), List.of("Working without AI first"),
                List.of("Set one AI study habit"), List.of("Entry requirements vary"), List.of("Economics"),
                List.of("Finance"));
    }

    private static ProfessionalNarrativeContext professionalContext() {
        return new ProfessionalNarrativeContext("Software Architect", "I design complex systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(14), AssessmentStatus.READY,
                Arrays.stream(ProfessionalMeasure.values())
                        .map(measure -> new ProfessionalNarrativeContext.MeasureContext(measure, measure.name(), 6.5,
                                "Moderate"))
                        .toList(),
                List.of(new ProfessionalNarrativeContext.FactorContext("digital_executability", "Digital executability",
                        "Much of the work can be performed digitally", DecisionFactorEffect.INCREASES_AI_EXPOSURE,
                        ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY, 1, 3.5, 0.1)),
                List.of(), com.chikere.futureai.assessment.professional.application.AssessmentSource.JEV,
                "professional-v1", "professional-decision-v1");
    }
}
