package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentService;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContextFactory;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeValidator;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeVersions;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy.Journey;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeValidator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContextFactory;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeValidator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeVersions;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit live evaluation. Never selected by normal Surefire or verify. */
@Import({TestcontainersConfiguration.class, LiveOpenAiNarrativeIT.CountingConfiguration.class})
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.public-base-url=https://ai-career-impact.example",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "spring.ai.openai.chat.model=${OPENAI_MODEL:gpt-6-luna}",
        "spring.ai.typesafe.api-key=${TYPESAFE_API_KEY}",
        "spring.ai.typesafe.model=jev-latest"})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "openai.live.evaluation.enabled", matches = "true")
@EnabledIfSystemProperty(named = "openai.eval.case", matches = "university|a-level|professional|university-calibration|a-level-calibration|professional-calibration")
class LiveOpenAiNarrativeIT {

    private static final Pattern JUDGMENT_ROW = Pattern.compile(
            "^\\| `([^`]+)` \\| ([0-9.]+) \\| (.*?) \\| ([0-9.]+|—) \\| (.*?) ?\\|$");
    private static final Path REPORT_DIRECTORY = Path.of("target", "openai-evaluation");

    @Autowired UniversityAssessmentStore universityAssessments;
    @Autowired ALevelAssessmentStore aLevelAssessments;
    @Autowired UniversityNarrativeStore universityNarratives;
    @Autowired ALevelNarrativeStore aLevelNarratives;
    @Autowired OpenAiUniversityNarrativeGenerator universityGenerator;
    @Autowired OpenAiALevelNarrativeGenerator aLevelGenerator;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProfessionalAssessmentService professionalAssessmentService;
    @Autowired ProfessionalAssessmentStore professionalAssessments;
    @Autowired ProfessionalNarrativeService professionalNarratives;
    @Autowired OpenAiProfessionalNarrativeGenerator professionalGenerator;
    @Autowired CallCounts callCounts;
    @LocalServerPort int port;


    @Test
    void evaluateSelectedCase() throws Exception {
        switch (System.getProperty("openai.eval.case")) {
            case "university", "university-calibration" -> university();
            case "a-level", "a-level-calibration" -> aLevel();
            case "professional" -> endToEndProfessional();
            case "professional-calibration" -> professionalCalibration();
            default -> throw new IllegalArgumentException("Unrecognised live OpenAI case");
        }
    }

    private void university() throws Exception {
        AssessmentJudgmentSet judgments = replayJudgments("university-v1-report.md", JourneyType.UNIVERSITY,
                "university-v1");
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        assertThat(decision.assessmentDecision().status().name()).isEqualTo("READY_WITH_CAUTION");
        assertThat(decision.measures().get(com.chikere.futureai.decision.university.UniversityMeasure.FUTURE_READINESS)
                .indicator()).isCloseTo(8.538, org.assertj.core.data.Offset.offset(0.005));
        var state = new UniversityAssessmentState("Computer Science",
                "Study of software design, algorithms, data structures, databases, computer systems and "
                        + "mathematics through programming projects and a final-year project.",
                List.of("Algorithms and data structures", "Software engineering", "Databases",
                        "Computer systems", "Mathematics", "Final-year project"),
                List.of("Programming", "Problem solving", "System design", "Teamwork"),
                List.of(), Optional.empty(), Optional.of("United Kingdom"));
        UUID id = UUID.randomUUID();
        var assessment = new StoredUniversityAssessment(id, Instant.now(), state, judgments,
                new UniversityAssessmentResult(decision,
                        com.chikere.futureai.assessment.university.application.AssessmentSource.JEV,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest"));
        universityAssessments.save(assessment);
        var context = new UniversityNarrativeContextFactory().create(assessment);
        Instant started = Instant.now();
        universityNarratives.save(new StoredUniversityNarrative(id,
                com.chikere.futureai.narrative.university.application.NarrativeStatus.PENDING,
                Optional.empty(), universityGenerator.source(), universityGenerator.providerIdentifier(),
                universityGenerator.modelIdentifier(), UniversityNarrativeVersions.PROMPT_AND_SCHEMA,
                started, Optional.empty()));
        long start = System.nanoTime();
        UniversityNarrative narrative = null;
        String failure = "none";
        String stage = "structured output";
        try {
            narrative = universityGenerator.generate(context);
            stage = "validator";
            narrative = new UniversityNarrativeValidator().validate(narrative);
        }
        catch (RuntimeException exception) {
            failure = stage + ": " + exception.getClass().getSimpleName();
            narrative = null;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        var status = narrative == null
                ? com.chikere.futureai.narrative.university.application.NarrativeStatus.FAILED
                : com.chikere.futureai.narrative.university.application.NarrativeStatus.AVAILABLE;
        universityNarratives.save(new StoredUniversityNarrative(id, status, Optional.ofNullable(narrative),
                universityGenerator.source(), universityGenerator.providerIdentifier(),
                universityGenerator.modelIdentifier(), UniversityNarrativeVersions.PROMPT_AND_SCHEMA,
                started, narrative == null ? Optional.empty() : Optional.of(Instant.now())));
        var reloaded = universityNarratives.findByAssessmentId(id).orElseThrow();
        writeReport("university", id, elapsed, stage, failure, reloaded.status().name(), narrative);
        assertThat(reloaded.status().name()).isEqualTo("AVAILABLE");
        assertThat(reloaded.narrative()).isPresent();
    }

    private void aLevel() throws Exception {
        AssessmentJudgmentSet judgments = replayJudgments("a-level-v1-report.md", JourneyType.A_LEVEL,
                "a-level-v1");
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        assertThat(decision.assessmentDecision().status().name()).isEqualTo("READY_WITH_CAUTION");
        assertThat(decision.measures().get(com.chikere.futureai.decision.alevel.ALevelMeasure.FUTURE_OPTIONALITY)
                .indicator()).isCloseTo(7.226, org.assertj.core.data.Offset.offset(0.005));
        var state = new ALevelAssessmentState(
                List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving", "Analysing data and real-world issues"),
                List.of("Economics", "Geography"), List.of(),
                Optional.of("United Kingdom"), Optional.empty());
        UUID id = UUID.randomUUID();
        var assessment = new StoredALevelAssessment(id, Instant.now(), state, judgments,
                new ALevelAssessmentResult(decision,
                        com.chikere.futureai.assessment.alevel.application.AssessmentSource.JEV,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest"));
        aLevelAssessments.save(assessment);
        var context = new ALevelNarrativeContextFactory().create(assessment);
        Instant started = Instant.now();
        aLevelNarratives.save(new StoredALevelNarrative(id,
                com.chikere.futureai.narrative.alevel.application.NarrativeStatus.PENDING,
                Optional.empty(), aLevelGenerator.source(), aLevelGenerator.providerIdentifier(),
                aLevelGenerator.modelIdentifier(), ALevelNarrativeVersions.PROMPT_AND_SCHEMA,
                started, Optional.empty()));
        long start = System.nanoTime();
        ALevelNarrative narrative = null;
        String failure = "none";
        String stage = "structured output";
        try {
            narrative = aLevelGenerator.generate(context);
            stage = "validator";
            narrative = new ALevelNarrativeValidator().validate(narrative, context);
        }
        catch (RuntimeException exception) {
            failure = stage + ": " + exception.getClass().getSimpleName();
            narrative = null;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        var status = narrative == null
                ? com.chikere.futureai.narrative.alevel.application.NarrativeStatus.FAILED
                : com.chikere.futureai.narrative.alevel.application.NarrativeStatus.AVAILABLE;
        aLevelNarratives.save(new StoredALevelNarrative(id, status, Optional.ofNullable(narrative),
                aLevelGenerator.source(), aLevelGenerator.providerIdentifier(),
                aLevelGenerator.modelIdentifier(), ALevelNarrativeVersions.PROMPT_AND_SCHEMA,
                started, narrative == null ? Optional.empty() : Optional.of(Instant.now())));
        var reloaded = aLevelNarratives.findByAssessmentId(id).orElseThrow();
        writeReport("a-level", id, elapsed, stage, failure, reloaded.status().name(), narrative);
        assertThat(reloaded.status().name()).isEqualTo("AVAILABLE");
        assertThat(reloaded.narrative()).isPresent();
    }

    private void endToEndProfessional() throws Exception {
        var state = ProfessionalEvaluationDataset.select(ProfessionalEvaluationDataset.load(),
                "senior_software_architect").getFirst().toAssessmentState();
        long jevStart = System.nanoTime();
        UUID id = professionalAssessmentService.submitProfessionalAssessment(state);
        long jevElapsed = (System.nanoTime() - jevStart) / 1_000_000;
        var assessment = professionalAssessments.findById(id).orElseThrow();
        assertThat(assessment.result().source().name()).isEqualTo("JEV");
        long narrativeStart = System.nanoTime();
        var generated = professionalNarratives.generateFor(id);
        long elapsed = (System.nanoTime() - narrativeStart) / 1_000_000;
        var saved = professionalNarratives.findFor(id).orElseThrow();
        assertThat(saved).isEqualTo(generated);
        String validator = "not reached";
        if (callCounts.structuredOutput.get() != null) {
            try {
                new ProfessionalNarrativeValidator().validate(callCounts.structuredOutput.get());
                validator = "passed";
            }
            catch (RuntimeException failure) {
                validator = "failed: " + failure.getClass().getSimpleName();
            }
        }
        String failure = callCounts.generatorFailure.get() == null ? "none" : callCounts.generatorFailure.get();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/professional-assessment/" + id)).GET().build();
        var client = HttpClient.newHttpClient();
        HttpResponse<String> first = client.send(request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> second = client.send(request, HttpResponse.BodyHandlers.ofString());
        writeReport("professional", id, elapsed, "validator", failure, saved.status().name(),
                saved.narrative().orElse(null));
        Files.writeString(REPORT_DIRECTORY.resolve("professional-report.md"),
                "\n- Jev and Java policy elapsed: " + jevElapsed + " ms\n"
                        + "- Independent validator check: " + validator + "\n"
                        + "- First and second rendered result HTTP status: " + first.statusCode()
                        + ", " + second.statusCode() + "\n"
                        + "- Provider calls before and after reload: Jev " + callCounts.jev.get()
                        + ", OpenAI " + callCounts.openAi.get() + "\n",
                java.nio.file.StandardOpenOption.APPEND);
        assertThat(saved.status().name()).isEqualTo("AVAILABLE");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains(saved.narrative().orElseThrow().summary());
        assertThat(second.body()).contains(saved.narrative().orElseThrow().summary());
        assertThat(callCounts.jev.get()).isEqualTo(1);
        assertThat(callCounts.openAi.get()).isEqualTo(1);
    }

    private void professionalCalibration() throws Exception {
        var state = ProfessionalEvaluationDataset.select(ProfessionalEvaluationDataset.load(),
                "senior_software_architect").getFirst().toAssessmentState();
        var judgments = new com.chikere.futureai.judgment.fixture.professional
                .LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1()
                .decide(judgments);
        UUID id = UUID.randomUUID();
        var assessment = new com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment(
                id, Instant.now(), state, judgments,
                new com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult(
                        decision,
                        com.chikere.futureai.assessment.professional.application.AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        var context = new com.chikere.futureai.narrative.professional.application
                .ProfessionalNarrativeContextFactory().create(assessment);
        long start = System.nanoTime();
        ProfessionalNarrative narrative = null;
        String failure = "none";
        String stage = "structured output";
        try {
            narrative = professionalGenerator.generate(context);
            stage = "validator";
            narrative = new ProfessionalNarrativeValidator().validate(narrative);
        }
        catch (RuntimeException exception) {
            failure = stage + ": " + exception.getClass().getSimpleName();
            narrative = null;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        writeReport("professional", id, elapsed, stage, failure,
                narrative == null ? "FAILED" : "NOT_PERSISTED_CALIBRATION", narrative);
        assertThat(callCounts.jev.get()).isZero();
        assertThat(narrative).isNotNull();
    }

    private AssessmentJudgmentSet replayJudgments(String fileName, JourneyType journey, String schema)
            throws Exception {
        List<DimensionJudgment> judgments = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("target", "jev-evaluation", fileName))) {
            Matcher row = JUDGMENT_ROW.matcher(line);
            if (!row.matches()) {
                continue;
            }
            String id = row.group(1);
            LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
            for (String token : row.group(5).strip().split("\\s+")) {
                if (token.contains("=")) {
                    String[] pair = token.split("=", 2);
                    probabilities.put(pair[0], Double.parseDouble(pair[1]));
                }
            }
            judgments.add(new DimensionJudgment(id, id,
                    OptionalDouble.of(Double.parseDouble(row.group(2))),
                    row.group(3).equals("—") ? Optional.empty() : Optional.of(row.group(3)),
                    Optional.empty(), row.group(4).equals("—") ? OptionalDouble.empty()
                            : OptionalDouble.of(Double.parseDouble(row.group(4))), probabilities));
        }
        assertThat(judgments).isNotEmpty();
        return new AssessmentJudgmentSet(journey, schema, judgments);
    }

    private void writeReport(String name, UUID id, long elapsed, String stage, String failure,
            String status, Object narrative) throws Exception {
        Files.createDirectories(REPORT_DIRECTORY);
        boolean calibration = System.getProperty("openai.eval.case", "").endsWith("-calibration");
        Path path = REPORT_DIRECTORY.resolve(name + (calibration ? "-calibration" : "") + "-report.md");
        String body = "# Live OpenAI narrative: " + name + "\n\n"
                + "- Assessment ID: `" + id + "`\n"
                + "- Configured model: `" + journeyModel(name) + "`\n"
                + "- Elapsed: " + elapsed + " ms\n"
                + "- Completed stage: " + stage + "\n"
                + "- Failure: " + failure + "\n"
                + "- Persisted narrative status: `" + status + "`\n"
                + "- Provider token usage and retry count: not exposed by this harness\n\n"
                + (calibration && name.equals("professional")
                        ? "- Assessment input: local fixture judgments through Java policy; no Jev call\n\n" : "")
                + "```json\n" + (narrative == null ? "null" :
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(narrative))
                + "\n```\n";
        Files.writeString(path, body);
        System.out.println("Live OpenAI narrative report written to " + path.toAbsolutePath());
    }

    static final class CallCounts {
        final AtomicInteger jev = new AtomicInteger();
        final AtomicInteger openAi = new AtomicInteger();
        final AtomicReference<ProfessionalNarrative> structuredOutput = new AtomicReference<>();
        final AtomicReference<String> generatorFailure = new AtomicReference<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingConfiguration {
        @Bean CallCounts callCounts() {
            return new CallCounts();
        }

        @Bean @Primary ProfessionalJudgmentProvider countingProfessionalJudgments(
                ProfessionalJevJudgmentService delegate, CallCounts counts) {
            return new ProfessionalJudgmentProvider() {
                @Override
                public AssessmentJudgmentSet judge(com.chikere.futureai.assessment.domain.ProfessionalAssessmentState state) {
                    counts.jev.incrementAndGet();
                    return delegate.judge(state);
                }

                @Override
                public com.chikere.futureai.assessment.professional.application.AssessmentSource source() {
                    return delegate.source();
                }

                @Override
                public Optional<String> modelIdentifier() {
                    return delegate.modelIdentifier();
                }
            };
        }

        @Bean @Primary ProfessionalNarrativeGenerator countingProfessionalNarrative(
                OpenAiProfessionalNarrativeGenerator delegate, CallCounts counts) {
            return new ProfessionalNarrativeGenerator() {
                @Override
                public ProfessionalNarrative generate(ProfessionalNarrativeContext context) {
                    counts.openAi.incrementAndGet();
                    try {
                        ProfessionalNarrative result = delegate.generate(context);
                        counts.structuredOutput.set(result);
                        return result;
                    }
                    catch (RuntimeException failure) {
                        counts.generatorFailure.set(failure.getClass().getSimpleName());
                        throw failure;
                    }
                }

                @Override
                public com.chikere.futureai.narrative.professional.application.NarrativeSource source() {
                    return delegate.source();
                }

                @Override
                public String providerIdentifier() {
                    return delegate.providerIdentifier();
                }

                @Override
                public Optional<String> modelIdentifier() {
                    return delegate.modelIdentifier();
                }
            };
        }
    }

    /** The narrative model the case's journey uses: its journey setting, or that journey's default. */
    private static String journeyModel(String name) {
        Journey journey = name.startsWith("university") ? Journey.UNIVERSITY
                : name.startsWith("a-level") ? Journey.A_LEVEL : Journey.PROFESSIONAL;
        String variable = Map.of(Journey.UNIVERSITY, "UNIVERSITY_NARRATIVE_MODEL", Journey.A_LEVEL,
                "A_LEVEL_NARRATIVE_MODEL", Journey.PROFESSIONAL, "PROFESSIONAL_NARRATIVE_MODEL").get(journey);
        return System.getenv().getOrDefault(variable, journey.defaultModel());
    }
}
