package com.chikere.futureai.livesmoke;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.fixture.LocalFixtureALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.fixture.LocalFixtureUniversityNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.university.application.UniversityPaymentGateway;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The live-smoke profile started as it would be launched, with a real database, the normal security chain and
 * the profile's own property file. Only the six provider adapters are replaced, by mocks answering with the
 * repository fixtures, so this test makes no Jev or OpenAI call; which real adapters the profile selects is
 * proven separately by {@link LiveSmokeProviderWiringTest}.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("live-smoke")
@AutoConfigureMockMvc
@SpringBootTest(properties = {"app.live-smoke.enabled=true", "TYPESAFE_API_KEY=test-only-typesafe-secret",
        "OPENAI_API_KEY=test-only-openai-secret", "app.live-smoke.trace-directory=target/live-smoke-test/traces"})
class LiveSmokeModeTest {

    private static final String BANNER =
            "LIVE SMOKE TEST MODE — real Jev and OpenAI calls are being made. Payments are bypassed.";

    @MockitoBean ProfessionalJudgmentProvider professionalJudgments;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrative;
    @MockitoBean UniversityJudgmentProvider universityJudgments;
    @MockitoBean UniversityNarrativeGenerator universityNarrative;
    @MockitoBean ALevelJudgmentProvider aLevelJudgments;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrative;
    @MockitoBean com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator paidNarrative;
    @MockitoBean com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider
            responsibilityProvider;

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired LiveSmokeCaseCatalog catalog;
    @Autowired LiveSmokeEntitlements entitlements;
    @Autowired LiveSmokeTraceFiles traceFiles;
    @Autowired ProfessionalPurchaseStore professionalPurchases;
    @Autowired ProfessionalReportPurchaseUseCase professionalPurchase;
    @Autowired UniversityReportPurchaseUseCase universityPurchase;
    @Autowired ALevelReportPurchaseUseCase aLevelPurchase;
    @Autowired Validator validator;

    @BeforeEach
    void providersAnswerWithRepositoryFixtures() {
        when(professionalJudgments.judge(any())).thenAnswer(call -> new LocalFixtureProfessionalJudgmentProvider(
                "registered_nurse").judge(call.<ProfessionalAssessmentState>getArgument(0)));
        when(professionalJudgments.source()).thenReturn(
                com.chikere.futureai.assessment.professional.application.AssessmentSource.JEV);
        when(professionalNarrative.generate(any())).thenAnswer(call ->
                new LocalFixtureProfessionalNarrativeGenerator().generate(call.getArgument(0)));
        when(professionalNarrative.source()).thenReturn(
                com.chikere.futureai.narrative.professional.application.NarrativeSource.OPENAI);
        when(professionalNarrative.providerIdentifier()).thenReturn("test-double");
        when(universityJudgments.judge(any())).thenAnswer(call -> new LocalFixtureUniversityJudgmentProvider(
                "computer_science").judge(call.<UniversityAssessmentState>getArgument(0)));
        when(universityJudgments.source()).thenReturn(
                com.chikere.futureai.assessment.university.application.AssessmentSource.JEV);
        when(universityNarrative.generate(any())).thenAnswer(call ->
                new LocalFixtureUniversityNarrativeGenerator().generate(call.getArgument(0)));
        when(universityNarrative.source()).thenReturn(
                com.chikere.futureai.narrative.university.application.NarrativeSource.OPENAI);
        when(universityNarrative.providerIdentifier()).thenReturn("test-double");
        when(aLevelJudgments.judge(any())).thenAnswer(call -> new LocalFixtureALevelV2JudgmentProvider(
                "maths_economics_geography").judge(call.<ALevelJudgmentInput>getArgument(0)));
        when(aLevelJudgments.source()).thenReturn(
                com.chikere.futureai.assessment.alevel.application.AssessmentSource.JEV);
        when(aLevelNarrative.generate(any())).thenAnswer(call ->
                new LocalFixtureALevelNarrativeGenerator().generate(call.getArgument(0)));
        when(aLevelNarrative.source()).thenReturn(com.chikere.futureai.narrative.alevel.application.NarrativeSource.OPENAI);
        when(aLevelNarrative.providerIdentifier()).thenReturn("test-double");
        when(responsibilityProvider.judge(any())).thenAnswer(call -> com.chikere.futureai.responsibility.professional
                .analysis.HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0)));
    }

    @Test
    void theResponsibilityAnalysisIsGeneratedOnceForAnEntitledSmokeAssessmentAndThenOnlyReused() throws Exception {
        UUID id = submit(catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow());
        String path = "/dev/live-smoke/professional/" + id + "/responsibility-analysis";
        assertThat(mvc.perform(get("/professional-assessment/" + id)).andReturn().getResponse().getContentAsString())
                .contains("Run responsibility analysis", path);
        mvc.perform(get(path)).andExpect(status().isNotFound());

        mvc.perform(post(path)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf())).andExpect(status().isSeeOther())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", path));
        mvc.perform(post(path).with(csrf())).andExpect(status().isSeeOther());
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());

        String json = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"schemaVersion\" : \"professional-responsibility-v1\"",
                "\"policyVersion\" : \"professional-responsibility-policy-v1\"", "\"status\" : \"COMPLETED\"",
                "Monitor patients and assess changes in their condition",
                "Follow clinical procedures and safety requirements", "GOOD_AI_ASSISTANCE_OPPORTUNITY");
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());

        mvc.perform(post("/dev/live-smoke/professional/" + UUID.randomUUID() + "/responsibility-analysis").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void thePaidGuidanceIsGeneratedOnceWithItsAnalysisAndThenOnlyReused() throws Exception {
        UUID id = submit(catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow());
        String path = "/dev/live-smoke/professional/" + id + "/responsibility-guidance";
        assertThat(mvc.perform(get("/professional-assessment/" + id)).andReturn().getResponse().getContentAsString())
                .contains("Generate paid guidance", path);
        mvc.perform(get(path)).andExpect(status().isNotFound());

        mvc.perform(post(path)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf())).andExpect(status().isSeeOther())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", path));
        mvc.perform(post(path).with(csrf())).andExpect(status().isSeeOther());
        // The analysis was generated through the analysis service exactly once.
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());

        String json = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"policyVersion\" : \"professional-guidance-policy-v1\"", "\"opportunities\"",
                "\"humanValueAreas\"", "\"skillPriorities\"", "\"days1To30\"", "\"safeguards\"");
        mvc.perform(get("/dev/live-smoke/professional/" + id + "/responsibility-analysis")).andExpect(status().isOk());
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());

        mvc.perform(post("/dev/live-smoke/professional/" + UUID.randomUUID() + "/responsibility-guidance").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void theFullDetailedReportV3IsPreparedOnceThroughTheNormalRouteAndItsSnapshotAndPdfAreReusable() throws Exception {
        when(paidNarrative.providerIdentifier()).thenReturn("test-double");
        when(paidNarrative.modelIdentifier()).thenReturn(java.util.Optional.of("test-model"));
        when(paidNarrative.generate(any())).thenAnswer(call -> new com.chikere.futureai.narrative.professional.paid
                .ProfessionalPaidNarrativeGenerator.GeneratedPaidNarrative(com.chikere.futureai.report.professional.detailed
                .DetailedReportFixtures.validNarrative(call.getArgument(0)), java.util.Optional.of("req_smoke"),
                java.util.Optional.of("test-model")));
        UUID id = submit(catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow());
        String result = mvc.perform(get("/professional-assessment/" + id)).andReturn().getResponse().getContentAsString();
        assertThat(result).contains("Prepare detailed report v3", "/professional-assessment/" + id + "/report/prepare",
                "/dev/live-smoke/professional/" + id + "/detailed-report");
        mvc.perform(get("/dev/live-smoke/professional/" + id + "/detailed-report")).andExpect(status().isNotFound());

        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(status().is3xxRedirection());
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());
        org.mockito.Mockito.verify(paidNarrative, org.mockito.Mockito.times(1)).generate(any());

        String page = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v3")).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Your work, responsibility by responsibility", "Your 90-day plan",
                "Live smoke: detailed report", "paid narrative AVAILABLE", "This is a sensible place to try AI");
        String json = mvc.perform(get("/dev/live-smoke/professional/" + id + "/detailed-report")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"reportVersion\" : \"professional-report-v3\"",
                "\"pdfVersion\" : \"professional-report-pdf-v5\"", "\"paidNarrativeStatus\" : \"AVAILABLE\"");
        byte[] pdf = mvc.perform(get("/professional-assessment/" + id + "/report.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).startsWith("%PDF-".getBytes());
        assertThat(mvc.perform(get("/professional-assessment/" + id + "/report.pdf")).andReturn().getResponse()
                .getContentAsByteArray()).isEqualTo(pdf);
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());
        org.mockito.Mockito.verify(paidNarrative, org.mockito.Mockito.times(1)).generate(any());
    }

    @Test
    void aCustomAssessmentWithMoreThanEightResponsibilitiesExercisesTheSelectionPathAndFreezesTheChoice() throws Exception {
        when(paidNarrative.providerIdentifier()).thenReturn("test-double");
        when(paidNarrative.generate(any())).thenAnswer(call -> new com.chikere.futureai.narrative.professional.paid
                .ProfessionalPaidNarrativeGenerator.GeneratedPaidNarrative(com.chikere.futureai.report.professional.detailed
                .DetailedReportFixtures.validNarrative(call.getArgument(0)), java.util.Optional.empty(),
                java.util.Optional.empty()));
        var nurse = catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow();
        var fields = new java.util.HashMap<String, Object>(nurse.fields());
        java.util.List<String> ten = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "Custom responsibility number " + i).toList();
        fields.put("responsibilities", String.join("\n", ten));
        UUID id = submit(new LiveSmokeCase("custom-ten", LiveSmokeJourney.PROFESSIONAL, "Custom ten",
                "Test-only custom assessment", fields));
        String path = "/professional-assessment/" + id + "/report";

        String page = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Choose up to 8 responsibilities for detailed analysis")
                .containsSubsequence(ten.toArray(String[]::new)).doesNotContain("checked");
        // Nothing chosen, nine chosen, or no selection at all: rejected without any Jev call.
        mvc.perform(post(path + "/prepare").with(csrf()).param("selectionForm", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        path + "?preparation=invalid-selection"));
        mvc.perform(post(path + "/prepare").with(csrf()).param("selectionForm", "true")
                        .param("responsibility", "1", "2", "3", "4", "5", "6", "7", "8", "9"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        path + "?preparation=invalid-selection"));
        mvc.perform(post(path + "/prepare").with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        path + "?preparation=selection-required"));
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.never()).judge(any());

        // A valid choice, submitted out of order, is analysed in the original order.
        mvc.perform(post(path + "/prepare").with(csrf()).param("selectionForm", "true")
                .param("responsibility", "10", "3", "7")).andExpect(status().is3xxRedirection());
        var request = org.mockito.ArgumentCaptor.forClass(
                com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest.class);
        org.mockito.Mockito.verify(responsibilityProvider).judge(request.capture());
        assertThat(request.getValue().responsibilities()).containsExactly("Custom responsibility number 3",
                "Custom responsibility number 7", "Custom responsibility number 10");
        String report = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v3")).andReturn().getResponse().getContentAsString();
        assertThat(report).containsSubsequence("Custom responsibility number 3", "Custom responsibility number 7",
                "Custom responsibility number 10").doesNotContain("Custom responsibility number 1<",
                "Choose up to 8 responsibilities");
        // The choice is frozen: a later, different selection changes nothing and calls nothing.
        mvc.perform(post(path + "/prepare").with(csrf()).param("selectionForm", "true").param("responsibility", "1", "2"))
                .andExpect(status().is3xxRedirection());
        org.mockito.Mockito.verify(responsibilityProvider, org.mockito.Mockito.times(1)).judge(any());
        org.mockito.Mockito.verify(paidNarrative, org.mockito.Mockito.times(1)).generate(any());
    }

    @Test
    void theDashboardListsEveryCaseAndThisRunsConfigurationWithoutSecretsAndSubmitsNothing() throws Exception {
        String html = mvc.perform(get("/dev/live-smoke")).andExpect(status().isOk())
                .andExpect(view().name("dev/live-smoke")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains(BANNER, "Professional", "University", "A-level", "Registered Nurse",
                "Java Software Developer", "Management Accountant", "BSc Computer Science", "Economics", "History",
                "Mathematics, Economics and Geography", "Mathematics and Physics", "Open pre-filled form",
                "/dev/live-smoke/professional/registered-nurse", "/dev/live-smoke/a-level/maths-economics-geography",
                "Stripe", "BYPASSED", "Assessment trace", "ENABLED", "Database");
        assertThat(html).doesNotContain("test-only-typesafe-secret", "test-only-openai-secret", "sk_", "whsec_",
                "password");
        assertThat(html).contains("New custom Professional assessment", "New custom University assessment",
                "New custom A-level assessment", "/dev/live-smoke/professional/new", "/dev/live-smoke/university/new",
                "/dev/live-smoke/a-level/new");
        assertThat(html).contains("Electrician", "Secondary-school Teacher", "Solicitor", "Customer-service Adviser",
                "/dev/live-smoke/professional/customer-service-adviser");
        assertThat(catalog.all()).hasSize(20);
    }

    @Test
    void everyCasePrefillsTheNormalFormWhichPassesTheNormalValidation() throws Exception {
        for (LiveSmokeCase smokeCase : catalog.all()) {
            String html = mvc.perform(get("/dev/live-smoke/" + smokeCase.journey().slug() + "/" + smokeCase.id()))
                    .andExpect(status().isOk()).andExpect(view().name(smokeCase.journey().formView()))
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as(smokeCase.id()).contains(BANNER,
                    "action=\"" + smokeCase.journey().basePath() + "\"", "name=\"_csrf\"");
            assertThat(validator.validate(catalog.form(smokeCase))).as(smokeCase.id()).isEmpty();
        }
        String nurse = mvc.perform(get("/dev/live-smoke/professional/registered-nurse"))
                .andReturn().getResponse().getContentAsString();
        assertThat(nurse).contains("value=\"Registered Nurse\"", "I care for patients on a hospital ward.",
                "Follow clinical procedures and safety requirements", "Working under pressure", "value=\"8\"");
        mvc.perform(get("/dev/live-smoke/professional/no-such-case")).andExpect(status().isNotFound());
        mvc.perform(get("/dev/live-smoke/unknown-journey/registered-nurse")).andExpect(status().isNotFound());
    }

    @Test
    void aSubmittedSmokeCaseUnlocksTheDetailedReportAndPdfWithoutStripeOnce() throws Exception {
        assertThat(context.getBeansOfType(ProfessionalPaymentGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(UniversityPaymentGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(ALevelPaymentGateway.class)).isEmpty();

        try (TraceLogCapture log = TraceLogCapture.start()) {
            UUID id = submit(catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow());

            assertThat(log.events(TraceStage.START)).isNotEmpty()
                    .allSatisfy(event -> assertThat(event.get("executionMode").asText()).isEqualTo("LIVE_SMOKE"));
            var entitlement = professionalPurchases.findEntitlementByAssessmentId(id).orElseThrow();
            assertThat(entitlement.status()).isEqualTo(ProfessionalEntitlementStatus.ACTIVE);
            assertThat(entitlement.paymentProvider()).isEqualTo(ProfessionalPaymentProvider.COMPLIMENTARY)
                    .isNotEqualTo(ProfessionalPaymentProvider.STRIPE);
            assertThat(entitlement.providerPaymentReference()).isEmpty();
            assertThat(entitlement.providerCheckoutSessionId()).startsWith("complimentary_").doesNotStartWith("cs_");
            assertThat(professionalPurchase.accessFor(id).checkoutAvailable()).isFalse();

            String result = mvc.perform(get("/professional-assessment/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(result).containsSubsequence("Detailed Report", "Download PDF", "Download Trace",
                    "Run Again (Registered Nurse)", "Back to Live Smoke Dashboard");
            assertThat(result).contains(BANNER, "/professional-assessment/" + id + "/report",
                    "/professional-assessment/" + id + "/report.pdf", "/dev/live-smoke/traces/" + id,
                    "/dev/live-smoke/professional/registered-nurse");
            mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk());
            byte[] pdf = mvc.perform(get("/professional-assessment/" + id + "/report.pdf")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

            // Idempotent: granting again, or reopening the result, never creates a second entitlement.
            assertThat(entitlements.grant(LiveSmokeJourney.PROFESSIONAL, id))
                    .contains(ComplimentaryRedemptionResult.ALREADY_ACTIVE);
            mvc.perform(get("/professional-assessment/" + id)).andExpect(status().isOk());
            assertThat(professionalPurchases.findEntitlementByAssessmentId(id).orElseThrow().entitlementId())
                    .isEqualTo(entitlement.entitlementId());
        }
    }

    @Test
    void universityAndALevelSmokeCasesAlsoReachTheirDetailedReportsAndPdfsWithoutPayment() throws Exception {
        UUID university = submit(catalog.find(LiveSmokeJourney.UNIVERSITY, "computer-science").orElseThrow());
        assertThat(universityPurchase.accessFor(university).status().map(Enum::name)).contains("ACTIVE");
        // university-report-v2: the complimentary unlock opens the preparation step. The report is generated only
        // by an explicit POST, and its PDF exists only once a final report is stored.
        String universityReport = mvc.perform(get("/university-assessment/" + university + "/report"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(universityReport).contains("Prepare my detailed report");
        mvc.perform(get("/university-assessment/" + university + "/report.pdf")).andExpect(status().isForbidden());

        // a-level-report-v2: the complimentary unlock opens the preparation step. The report is generated only by an
        // explicit POST (which never calls Jev), and its PDF exists only once a final report is stored.
        UUID aLevel = submit(catalog.find(LiveSmokeJourney.A_LEVEL, "maths-economics-geography").orElseThrow());
        assertThat(aLevelPurchase.accessFor(aLevel).status().map(Enum::name)).contains("ACTIVE");
        assertThat(mvc.perform(get("/a-level-assessment/" + aLevel + "/report")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).contains("Prepare my detailed report");
        mvc.perform(get("/a-level-assessment/" + aLevel + "/report.pdf")).andExpect(status().isForbidden());
    }

    @Test
    void anEditedSubmissionIsStillUnlockedButOffersNoRerunAndCsrfIsStillRequired() throws Exception {
        var edited = catalog.find(LiveSmokeJourney.PROFESSIONAL, "management-accountant").orElseThrow();
        Map<String, Object> fields = new java.util.HashMap<>(edited.fields());
        fields.put("profession", "Senior Management Accountant");
        UUID id = submit(new LiveSmokeCase("edited", edited.journey(), "Edited", "test", fields));

        String result = mvc.perform(get("/professional-assessment/" + id)).andReturn().getResponse()
                .getContentAsString();
        assertThat(result).contains("Back to Live Smoke Dashboard", "Download Trace").doesNotContain("Run Again");
        assertThat(professionalPurchase.accessFor(id).status()).contains(ProfessionalEntitlementStatus.ACTIVE);

        mvc.perform(withFields(post("/professional-assessment"), edited.fields())).andExpect(status().isForbidden());
    }

    @Test
    void eachCustomRouteOpensItsJourneysNormalBlankForm() throws Exception {
        for (LiveSmokeJourney journey : LiveSmokeJourney.values()) {
            String html = mvc.perform(get("/dev/live-smoke/" + journey.slug() + "/new")).andExpect(status().isOk())
                    .andExpect(view().name(journey.formView())).andReturn().getResponse().getContentAsString();
            assertThat(html).as(journey.slug()).contains(BANNER, "action=\"" + journey.basePath() + "\"",
                    "name=\"_csrf\"");
            // The same blank form the journey's own GET route renders.
            String normal = mvc.perform(get(journey.basePath())).andReturn().getResponse().getContentAsString();
            assertThat(strip(html)).as(journey.slug()).isEqualTo(strip(normal));
        }
        mvc.perform(get("/dev/live-smoke/unknown/new")).andExpect(status().isNotFound());
    }

    @Test
    void eachAssessmentGetsItsOwnCompleteTraceFileWhichDownloadsAloneAndHoldsNoSecret() throws Exception {
        UUID nurse = submit(catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow());
        mvc.perform(get("/professional-assessment/" + nurse + "/report")).andExpect(status().isOk());
        var developer = catalog.find(LiveSmokeJourney.PROFESSIONAL, "java-software-developer").orElseThrow();
        Map<String, Object> custom = new java.util.HashMap<>(developer.fields());
        // A secret pasted into the form must still never reach the trace file.
        custom.put("workDescription", developer.fields().get("workDescription") + " Key test-only-openai-secret here.");
        UUID other = submit(new LiveSmokeCase("custom", developer.journey(), "Custom", "test", custom));

        var nurseLines = traceLines(nurse);
        var otherLines = traceLines(other);
        assertThat(nurseLines).extracting(line -> line.get("stage").asText()).contains("START", "FORM_INPUT",
                "DOMAIN_INPUT", "DECISION_MODEL_REQUEST", "DECISION_MODEL_RESPONSE", "INFORMATION_SUFFICIENCY",
                "ASSESSMENT_CONFIDENCE", "SCORE_CALCULATION", "FACTOR_SELECTION", "DETERMINISTIC_ASSESSMENT",
                "PERSISTENCE", "NARRATIVE_REQUEST", "NARRATIVE_VALIDATION", "NARRATIVE_OUTCOME", "SUMMARY",
                "LIVE_SMOKE_METADATA", "DETAILED_REPORT_COMPLETE", "REPORT_SUMMARY");
        assertThat(metadata(nurseLines).get("smokeCaseId").asText()).isEqualTo("registered-nurse");
        assertThat(metadata(otherLines).get("smokeCaseId").asText()).isEqualTo(LiveSmokeTraceFiles.CUSTOM);
        // No event of one assessment is in the other's file.
        assertThat(nurseLines).allSatisfy(line -> assertThat(line.path("assessmentId").asText(nurse.toString()))
                .isIn(nurse.toString(), "null", ""));
        assertThat(traceIds(nurseLines)).doesNotContainAnyElementsOf(traceIds(otherLines));
        assertThat(nurseLines).allSatisfy(line -> assertThat(line.path("executionMode").asText()).isEqualTo("LIVE_SMOKE"));

        String raw = java.nio.file.Files.readString(file(other));
        assertThat(raw).contains("[REDACTED]").doesNotContain("test-only-openai-secret", "test-only-typesafe-secret");

        var response = mvc.perform(get("/dev/live-smoke/traces/" + nurse)).andExpect(status().isOk()).andReturn()
                .getResponse();
        assertThat(response.getContentAsString()).isEqualTo(java.nio.file.Files.readString(file(nurse)));
        assertThat(response.getContentType()).startsWith("application/x-ndjson");
        assertThat(response.getHeader("Content-Disposition")).contains("registered-nurse-" + nurse + ".jsonl");
        mvc.perform(get("/dev/live-smoke/traces/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/dev/live-smoke/traces/not-a-uuid")).andExpect(status().isNotFound());
        mvc.perform(get("/dev/live-smoke/traces/..%2F..%2Fpom.xml")).andExpect(status().is4xxClientError());
        mvc.perform(get("/dev/live-smoke/traces/" + nurse + ".jsonl")).andExpect(status().isNotFound());
    }

    private java.nio.file.Path file(UUID id) {
        return traceFiles.fileFor(id).orElseThrow();
    }

    private java.util.List<com.fasterxml.jackson.databind.JsonNode> traceLines(UUID id) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.List<com.fasterxml.jackson.databind.JsonNode> lines = new java.util.ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(file(id))) {
            lines.add(json.readTree(line));
        }
        return lines;
    }

    private static com.fasterxml.jackson.databind.JsonNode metadata(
            java.util.List<com.fasterxml.jackson.databind.JsonNode> lines) {
        return lines.stream().filter(line -> line.get("stage").asText().equals("LIVE_SMOKE_METADATA")).findFirst()
                .orElseThrow();
    }

    private static java.util.Set<String> traceIds(java.util.List<com.fasterxml.jackson.databind.JsonNode> lines) {
        return lines.stream().map(line -> line.path("assessmentTraceId").asText(""))
                .filter(value -> !value.isEmpty() && !value.equals("null"))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** The page without its per-request CSRF token. */
    private static String strip(String html) {
        return html.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "");
    }

    private UUID submit(LiveSmokeCase smokeCase) throws Exception {
        String location = mvc.perform(withFields(post(smokeCase.journey().basePath()), smokeCase.fields()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith(smokeCase.journey().basePath() + "/");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private static MockHttpServletRequestBuilder withFields(MockHttpServletRequestBuilder request,
            Map<String, Object> fields) {
        fields.forEach((name, value) -> request.param(name, String.valueOf(value)));
        return request;
    }
}
