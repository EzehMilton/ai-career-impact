package com.chikere.futureai.trace;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContext;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.fixture.LocalFixtureALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContext;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.fixture.LocalFixtureUniversityNarrativeGenerator;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traces each journey from a real form post to the free result page and the detailed report, under the
 * dev profile's fixture providers. Credentials are configured with recognisable values so the test can
 * prove none of them reaches the trace.
 */
@Import({TestcontainersConfiguration.class, AssessmentTraceJourneyTest.CountingProviders.class})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "app.assessment-trace.enabled=true",
        "spring.ai.openai.api-key=" + AssessmentTraceJourneyTest.OPENAI_KEY,
        "spring.ai.typesafe.api-key=" + AssessmentTraceJourneyTest.JEV_KEY,
        "app.stripe.secret-key=" + AssessmentTraceJourneyTest.STRIPE_KEY,
        "app.stripe.webhook-secret=" + AssessmentTraceJourneyTest.WEBHOOK_SECRET,
        "app.complimentary-report.code=" + AssessmentTraceJourneyTest.ACCESS_CODE})
class AssessmentTraceJourneyTest {

    static final String OPENAI_KEY = "sk-proj-tracetestopenaikey0001";
    static final String JEV_KEY = "jev-tracetest-typesafe-key-0002";
    static final String STRIPE_KEY = "sk_test_tracetestStripeKey0003";
    static final String WEBHOOK_SECRET = "whsec_tracetestWebhookSecret0004";
    static final String ACCESS_CODE = "TRACETEST-ACCESS-CODE-0005";

    /** The major stages every submission passes through, in order. */
    private static final List<String> SUBMISSION_STAGES = List.of("START", "FORM_INPUT", "DOMAIN_INPUT",
            "DECISION_MODEL_REQUEST", "DECISION_MODEL_RESPONSE", "INFORMATION_SUFFICIENCY", "ASSESSMENT_CONFIDENCE",
            "SCORE_CALCULATION", "FACTOR_SELECTION", "DETERMINISTIC_ASSESSMENT", "PERSISTENCE", "NARRATIVE_REQUEST",
            "NARRATIVE_RESPONSE", "NARRATIVE_VALIDATION", "EVIDENCE_REQUEST", "EVIDENCE_RESPONSE", "SUMMARY");

    @Autowired MockMvc mvc;
    @Autowired ProviderCalls calls;
    @Autowired ProfessionalReportQuery professionalReports;
    @Autowired UniversityReportQuery universityReports;
    @Autowired ALevelReportQuery aLevelReports;

    private TraceLogCapture log;

    @BeforeEach
    void capture() {
        log = TraceLogCapture.start();
        calls.reset();
    }

    @AfterEach
    void detach() {
        log.close();
    }

    @Test
    void professionalIsTracedFromTheFormToTheDetailedReport() throws Exception {
        UUID id = submit(post("/professional-assessment").with(csrf())
                .param("profession", "  Software Architect ")
                .param("workDescription", "I design complex software systems and guide engineers.")
                .param("responsibilities", "Design systems\n\n Resolve trade-offs ")
                .param("skills", "Architecture\nCommunication").param("yearsOfExperience", "14"),
                "/professional-assessment/");

        assertSubmissionTraced(id, "senior_software_architect", 4);
        assertThat(log.stages()).contains("OCCUPATION_CLASSIFICATION");
        // The raw form and what it became after trimming and splitting are both visible.
        assertThat(log.only(TraceStage.FORM_INPUT).at("/payload/profession").asText()).isEqualTo("  Software Architect ");
        assertThat(log.only(TraceStage.DOMAIN_INPUT).at("/payload/state/profession").asText()).isEqualTo("Software Architect");
        assertThat(log.only(TraceStage.DOMAIN_INPUT).at("/payload/state/responsibilities/1").asText())
                .isEqualTo("Resolve trade-offs");

        assertReportsTraced(id, "/professional-assessment/",
                () -> professionalReports.assemble(id, ProfessionalReportTier.PAID));
    }

    @Test
    void universityIsTracedFromTheFormToTheDetailedReport() throws Exception {
        UUID id = submit(post("/university-assessment").with(csrf())
                .param("courseTitle", "Computer Science")
                .param("courseDescription", "Programming, algorithms, databases and a final-year project.")
                .param("keyModules", "Algorithms\nDatabases")
                .param("skillsDeveloped", "Programming\nProblem solving")
                .param("preferredCareerAreas", "Software engineering"),
                "/university-assessment/");

        assertSubmissionTraced(id, "computer_science", 5);
        assertThat(log.only(TraceStage.DOMAIN_INPUT).at("/payload/state/keyModules").size()).isEqualTo(2);

        assertReportsTraced(id, "/university-assessment/",
                () -> universityReports.assemble(id, UniversityReportTier.PAID));
    }

    @Test
    void aLevelIsTracedFromTheFormToTheDetailedReport() throws Exception {
        UUID id = submit(post("/a-level-assessment").with(csrf())
                .param("selectedSubjects", "Mathematics\nEconomics\nGeography")
                .param("predictedGrades", "Mathematics: A")
                .param("preferredDegreeAreas", "Economics"),
                "/a-level-assessment/");

        assertSubmissionTraced(id, "maths_economics_geography", 6);
        assertThat(log.only(TraceStage.FORM_INPUT).at("/payload/selectedSubjects").asText())
                .isEqualTo("Mathematics\nEconomics\nGeography");
        assertThat(log.only(TraceStage.DOMAIN_INPUT).at("/payload/state/selectedSubjects").size()).isEqualTo(3);

        assertReportsTraced(id, "/a-level-assessment/",
                () -> aLevelReports.assemble(id, ALevelReportTier.PAID));
    }

    private UUID submit(MockHttpServletRequestBuilder form, String route) throws Exception {
        String location = mvc.perform(form).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(location.substring(route.length()));
    }

    private void assertSubmissionTraced(UUID id, String fixtureId, int measures) {
        assertThat(log.stages()).containsSubsequence(SUBMISSION_STAGES);
        assertThat(log.events(TraceStage.SCORE_CALCULATION)).hasSize(measures);

        // One submission, one trace id, and the persisted assessment id from persistence onwards.
        List<JsonNode> events = log.events();
        String traceId = events.getFirst().get("assessmentTraceId").asText();
        assertThat(events).allSatisfy(event -> assertThat(event.get("assessmentTraceId").asText()).isEqualTo(traceId));
        int persisted = log.stages().indexOf("PERSISTENCE");
        assertThat(events.subList(persisted, events.size()))
                .allSatisfy(event -> assertThat(event.get("assessmentId").asText()).isEqualTo(id.toString()));

        // Fixture provenance is explicit, and each provider call is timed.
        JsonNode request = log.only(TraceStage.DECISION_MODEL_REQUEST).get("payload");
        assertThat(request.get("provider").asText()).isEqualTo("LOCAL_FIXTURE");
        assertThat(request.get("fixtureId").asText()).isEqualTo(fixtureId);
        JsonNode decision = log.only(TraceStage.DECISION_MODEL_RESPONSE).get("payload");
        assertThat(decision.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(decision.has("durationMs")).isTrue();
        assertThat(decision.at("/result/judgments").size()).isGreaterThan(measures);
        assertThat(log.only(TraceStage.NARRATIVE_REQUEST).at("/payload/provider").asText()).isEqualTo("LOCAL_FIXTURE");
        JsonNode narrative = log.only(TraceStage.NARRATIVE_RESPONSE).get("payload");
        assertThat(narrative.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(narrative.has("durationMs")).isTrue();
        assertThat(log.only(TraceStage.NARRATIVE_VALIDATION).at("/payload/outcome").asText()).isEqualTo("ACCEPTED");
        assertThat(log.only(TraceStage.DETERMINISTIC_ASSESSMENT).at("/payload/boundary").asText())
                .contains("FINAL DETERMINISTIC ASSESSMENT");

        JsonNode timings = log.only(TraceStage.SUMMARY).at("/payload/timings");
        for (String timing : List.of("formToDomainMs", "decisionModelMs", "decisionPolicyMs", "persistenceMs",
                "narrativeModelMs", "evidenceMs", "totalAssessmentMs")) {
            assertThat(timings.has(timing)).as(timing).isTrue();
        }

        // Tracing adds no provider calls of its own.
        assertThat(calls.judgments.get()).isEqualTo(1);
        assertThat(calls.narratives.get()).isEqualTo(1);
        assertNoSecrets();
    }

    private void assertReportsTraced(UUID id, String route, Runnable detailedReport) throws Exception {
        log.clear();
        mvc.perform(get(route + id)).andExpect(status().isOk());
        JsonNode free = log.only(TraceStage.FREE_ASSESSMENT_COMPLETE);
        assertThat(free.get("assessmentId").asText()).isEqualTo(id.toString());
        assertThat(free.at("/payload/tier").asText()).isEqualTo("FREE");
        assertThat(free.at("/payload/report").isObject()).isTrue();
        assertThat(log.only(TraceStage.REPORT_SUMMARY).at("/payload/timings").has("reportAssemblyMs")).isTrue();

        log.clear();
        detailedReport.run();
        JsonNode detailed = log.only(TraceStage.DETAILED_REPORT_COMPLETE);
        assertThat(detailed.get("assessmentId").asText()).isEqualTo(id.toString());
        assertThat(detailed.at("/payload/tier").asText()).isEqualTo("DETAILED");
        assertThat(detailed.at("/payload/report").isObject()).isTrue();
        assertThat(calls.judgments.get()).as("viewing reports re-runs nothing").isEqualTo(1);
        assertThat(calls.narratives.get()).isEqualTo(1);
        assertNoSecrets();
    }

    private void assertNoSecrets() {
        assertThat(log.all()).doesNotContain(OPENAI_KEY, JEV_KEY, STRIPE_KEY, WEBHOOK_SECRET, ACCESS_CODE);
    }

    static final class ProviderCalls {
        final AtomicInteger judgments = new AtomicInteger();
        final AtomicInteger narratives = new AtomicInteger();

        void reset() {
            judgments.set(0);
            narratives.set(0);
        }
    }

    /** Counts real provider calls by wrapping the dev profile's fixtures; the mock maker cannot spy on them. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CountingProviders {

        @Bean
        ProviderCalls providerCalls() {
            return new ProviderCalls();
        }

        @Bean
        @Primary
        ProfessionalJudgmentProvider countingProfessionalJudgments(LocalFixtureProfessionalJudgmentProvider fixture,
                ProviderCalls calls) {
            return new ProfessionalJudgmentProvider() {
                @Override
                public AssessmentJudgmentSet judge(ProfessionalAssessmentState state) {
                    calls.judgments.incrementAndGet();
                    return fixture.judge(state);
                }

                @Override
                public com.chikere.futureai.assessment.professional.application.AssessmentSource source() {
                    return fixture.source();
                }

                @Override
                public Optional<String> fixtureId() {
                    return fixture.fixtureId();
                }
            };
        }

        @Bean
        @Primary
        UniversityJudgmentProvider countingUniversityJudgments(LocalFixtureUniversityJudgmentProvider fixture,
                ProviderCalls calls) {
            return new UniversityJudgmentProvider() {
                @Override
                public AssessmentJudgmentSet judge(UniversityAssessmentState state) {
                    calls.judgments.incrementAndGet();
                    return fixture.judge(state);
                }

                @Override
                public com.chikere.futureai.assessment.university.application.AssessmentSource source() {
                    return fixture.source();
                }

                @Override
                public Optional<String> fixtureId() {
                    return fixture.fixtureId();
                }
            };
        }

        @Bean
        @Primary
        ALevelJudgmentProvider countingALevelJudgments(LocalFixtureALevelJudgmentProvider fixture,
                ProviderCalls calls) {
            return new ALevelJudgmentProvider() {
                @Override
                public AssessmentJudgmentSet judge(ALevelAssessmentState state) {
                    calls.judgments.incrementAndGet();
                    return fixture.judge(state);
                }

                @Override
                public com.chikere.futureai.assessment.alevel.application.AssessmentSource source() {
                    return fixture.source();
                }

                @Override
                public Optional<String> fixtureId() {
                    return fixture.fixtureId();
                }
            };
        }

        @Bean
        @Primary
        ProfessionalNarrativeGenerator countingProfessionalNarratives(LocalFixtureProfessionalNarrativeGenerator fixture,
                ProviderCalls calls) {
            return new ProfessionalNarrativeGenerator() {
                @Override
                public ProfessionalNarrative generate(ProfessionalNarrativeContext context) {
                    calls.narratives.incrementAndGet();
                    return fixture.generate(context);
                }

                @Override
                public com.chikere.futureai.narrative.professional.application.NarrativeSource source() {
                    return fixture.source();
                }

                @Override
                public String providerIdentifier() {
                    return fixture.providerIdentifier();
                }
            };
        }

        @Bean
        @Primary
        UniversityNarrativeGenerator countingUniversityNarratives(LocalFixtureUniversityNarrativeGenerator fixture,
                ProviderCalls calls) {
            return new UniversityNarrativeGenerator() {
                @Override
                public UniversityNarrative generate(UniversityNarrativeContext context) {
                    calls.narratives.incrementAndGet();
                    return fixture.generate(context);
                }

                @Override
                public com.chikere.futureai.narrative.university.application.NarrativeSource source() {
                    return fixture.source();
                }

                @Override
                public String providerIdentifier() {
                    return fixture.providerIdentifier();
                }
            };
        }

        @Bean
        @Primary
        ALevelNarrativeGenerator countingALevelNarratives(LocalFixtureALevelNarrativeGenerator fixture,
                ProviderCalls calls) {
            return new ALevelNarrativeGenerator() {
                @Override
                public ALevelNarrative generate(ALevelNarrativeContext context) {
                    calls.narratives.incrementAndGet();
                    return fixture.generate(context);
                }

                @Override
                public com.chikere.futureai.narrative.alevel.application.NarrativeSource source() {
                    return fixture.source();
                }

                @Override
                public String providerIdentifier() {
                    return fixture.providerIdentifier();
                }
            };
        }
    }
}
