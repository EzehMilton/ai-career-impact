package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Explicit live evaluation of professional-paid-narrative-v1 against REAL OpenAI for the seven calibration roles.
 * Never selected by normal Surefire or verify: run only with
 * {@code ./mvnw -Plive-paid-narrative verify} and OPENAI_API_KEY set.
 *
 * <p>The responsibility analyses are the captured seven-role live Jev probe (Jev is NOT called again: the provider is
 * a mock that must stay untouched). Guidance, the paid narrative with real validation and repair, report-v3 assembly
 * and the v5 PDF are all real. Results are written to {@code .live-smoke/paid-narrative/<timestamp>/}.</p>
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "openai.paid.narrative.live.enabled", matches = "true")
class ProfessionalPaidNarrativeLiveIT {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void sevenRolesAgainstRealOpenAi() throws Exception {
        boolean dryRun = Boolean.getBoolean("openai.paid.narrative.live.dry-run");
        String model = Optional.ofNullable(System.getenv("OPENAI_MODEL")).filter(v -> !v.isBlank()).orElse("gpt-4o-mini");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class,
                        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
                        org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class))
                .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
                        "spring.ai.openai.chat.model=" + model)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var generator = new OpenAiProfessionalPaidNarrativeGenerator(
                            context.getBean(ChatClient.Builder.class).build(), new ObjectMapper(), model);
                    if (!dryRun) {
                        run(generator, model);
                    }
                });
    }

    private void run(OpenAiProfessionalPaidNarrativeGenerator openAi, String model) throws Exception {
        Path directory = Path.of(".live-smoke", "paid-narrative", Instant.now().toString().replace(':', '-'));
        Files.createDirectories(directory);
        List<Map<String, Object>> results = new ArrayList<>();
        StringBuilder markdown = new StringBuilder("# Paid narrative live run\n\nModel: " + model + "\n\n");
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var chain = DetailedReportFixtures.chain(run);
            UUID id = chain.analysis().assessmentId();
            var recorder = new Recording(openAi);
            var purchases = new ProfessionalDetailedReportServiceTest.Purchases();
            purchases.snapshots.put(id, chain.role());
            Instant at = Instant.now();
            purchases.entitlements.put(id, new ProfessionalReportEntitlement(chain.role().entitlementId(), id,
                    ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY,
                    "live_" + id, URI.create("http://localhost/report"), "complimentary", Optional.empty(),
                    Optional.of("professional-report-v2"), at, Optional.of(at)));
            ProfessionalReportPurchaseUseCase purchaseUseCase = mock(ProfessionalReportPurchaseUseCase.class);
            when(purchaseUseCase.findEntitlement(any())).thenAnswer(call -> purchases.findEntitlementByAssessmentId(call.getArgument(0)));
            ProfessionalResponsibilityJudgmentProvider jev = mock(ProfessionalResponsibilityJudgmentProvider.class);
            var analysisStore = new ProfessionalDetailedReportServiceTest.AnalysisStore();
            analysisStore.snapshots.put(id, chain.analysis());
            var assessments = mock(com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore.class);
            var analyses = new ProfessionalResponsibilityAnalysisService(assessments, purchaseUseCase, () -> Optional.of(jev),
                    new ProfessionalResponsibilityPolicyV1(), analysisStore, Clock.systemUTC(), AssessmentTrace.disabled());
            var guidance = new ProfessionalResponsibilityGuidanceService(purchaseUseCase, analyses,
                    new ProfessionalGuidancePolicyV1(), new ProfessionalDetailedReportServiceTest.GuidanceStore(),
                    Clock.systemUTC(), AssessmentTrace.disabled());
            var narratives = new ProfessionalPaidNarrativeService(new ProfessionalDetailedReportServiceTest.NarrativeStore(),
                    () -> Optional.of(recorder), new ProfessionalPaidNarrativeValidator(), Clock.systemUTC(),
                    AssessmentTrace.disabled());
            var service = new ProfessionalDetailedReportService(purchases, assessments, analyses, guidance, narratives,
                    new ProfessionalDetailedReportAssembler(), new ProfessionalDetailedReportServiceTest.ReportStore(),
                    Clock.systemUTC(), AssessmentTrace.disabled());

            long started = System.nanoTime();
            var outcome = service.prepare(id, Optional.empty());
            long preparationMs = (System.nanoTime() - started) / 1_000_000;
            verifyNoInteractions(jev);

            var views = new ProfessionalDetailedReportViewMapper();
            var view = outcome.snapshot().map(views::toView).orElseGet(() -> views.toProvisionalView(outcome.report()));
            long pdfStarted = System.nanoTime();
            byte[] pdf = new OpenHtmlProfessionalDetailedReportPdfRenderer().render(view);
            long pdfMs = (System.nanoTime() - pdfStarted) / 1_000_000;
            Files.write(directory.resolve(run + ".pdf"), pdf);
            Files.writeString(directory.resolve(run + ".md"), ProfessionalDetailedReportPdfCalibrationTest.markdown(view)
                    + narrativeMarkdown(outcome.report()), StandardCharsets.UTF_8);

            // The deterministic content must be exactly the stored guidance, whatever the narrative did.
            var expected = new ProfessionalGuidancePolicyV1().decide(chain.analysis());
            boolean deterministicUnchanged = outcome.report().opportunities().items().stream()
                    .map(ProfessionalDetailedReport.Opportunity::key).toList()
                    .equals(expected.opportunities().stream().map(o -> o.responsibilityKey()).toList())
                    && outcome.report().humanValue().items().stream().map(ProfessionalDetailedReport.HumanValue::key).toList()
                    .equals(expected.humanValueAreas().stream().map(h -> h.responsibilityKey()).toList())
                    && outcome.report().skills().items().stream().map(ProfessionalDetailedReport.Skill::label).toList()
                    .equals(expected.skillPriorities().stream().map(s -> s.label()).toList())
                    && outcome.report().safeguards().items().stream().map(ProfessionalDetailedReport.Safeguard::guidance).toList()
                    .equals(expected.safeguards().stream().map(s -> s.guidance()).toList())
                    && outcome.report().plan().phases().stream().flatMap(p -> p.actions().stream())
                    .map(ProfessionalDetailedReport.Action::title).toList()
                    .equals(java.util.stream.Stream.of(expected.plan().days1To30(), expected.plan().days31To60(),
                            expected.plan().days61To90()).flatMap(List::stream).map(a -> a.title()).toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", DetailedReportFixtures.ROLES.get(run));
            result.put("calls", recorder.calls.size());
            result.put("firstAttemptFailedRule", recorder.repairRule.get());
            result.put("repairRequired", recorder.repairRule.get() != null);
            result.put("finalStatus", outcome.report().narrativeStatus());
            result.put("provisional", outcome.provisional());
            result.put("callDurationsMs", recorder.calls.stream().map(Call::durationMs).toList());
            result.put("callErrors", recorder.calls.stream().map(Call::error).toList());
            result.put("preparationMs", preparationMs);
            result.put("pdfRenderMs", pdfMs);
            result.put("pdfBytes", pdf.length);
            result.put("deterministicUnchanged", deterministicUnchanged);
            results.add(result);
            markdown.append("## ").append(result.get("role")).append("\n\n").append(JSON.writeValueAsString(result))
                    .append("\n\n");
            assertThat(deterministicUnchanged).as(run).isTrue();
            assertThat(outcome.report().narrativeStatus()).as(run).isNotNull();
            if (outcome.report().narrativeStatus() == PaidNarrativeStatus.AVAILABLE) {
                assertThat(recorder.calls).as(run).hasSizeBetween(1, 2);
            }
        }
        Files.writeString(directory.resolve("results.json"), JSON.writeValueAsString(results), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("README.md"), markdown.toString(), StandardCharsets.UTF_8);
        System.out.println("Paid narrative live results written to " + directory.toAbsolutePath());
    }

    private static String narrativeMarkdown(ProfessionalDetailedReport report) {
        StringBuilder out = new StringBuilder("\n## Narrative (" + report.narrativeStatus() + ")\n");
        if (report.scoresTogether() != null) {
            out.append("\n### Scores together\n").append(report.scoresTogether()).append("\n");
        }
        if (report.responsibilities().narrative() != null) {
            out.append("\n### Responsibility pattern\n").append(report.responsibilities().narrative()).append("\n");
        }
        out.append("\n### Opportunity explanations\n");
        report.opportunities().items().forEach(o -> out.append("- ").append(o.responsibility()).append(": ")
                .append(o.explanation()).append("\n"));
        out.append("\n### Human-value explanations\n");
        report.humanValue().items().forEach(h -> out.append("- ").append(h.responsibility()).append(": ")
                .append(h.explanation()).append("\n"));
        out.append("\n### Skills\n").append(report.skills().narrative()).append("\n");
        report.skills().items().forEach(s -> out.append("- ").append(s.label()).append(": ").append(s.explanation()).append("\n"));
        out.append("\n### Plan\n").append(report.plan().narrative()).append("\n");
        report.plan().phases().forEach(p -> p.actions().forEach(a -> out.append("- ").append(a.title()).append(" -> ")
                .append(a.note()).append("\n")));
        out.append("\n### Closing\n").append(report.closingPerspective()).append("\n");
        return out.toString();
    }

    record Call(int attempt, long durationMs, String error) {
    }

    /** The real generator, recording each call's duration and the rule that triggered a repair. */
    static final class Recording implements ProfessionalPaidNarrativeGenerator {
        private final ProfessionalPaidNarrativeGenerator delegate;
        final List<Call> calls = new ArrayList<>();
        final AtomicReference<String> repairRule = new AtomicReference<>();

        Recording(ProfessionalPaidNarrativeGenerator delegate) {
            this.delegate = delegate;
        }

        @Override
        public GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context) {
            return timed(1, () -> delegate.generate(context));
        }

        @Override
        public GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback) {
            repairRule.set(feedback.rule() + ": " + feedback.offendingSentence());
            return timed(2, () -> delegate.repair(context, feedback));
        }

        private GeneratedPaidNarrative timed(int attempt, java.util.function.Supplier<GeneratedPaidNarrative> call) {
            long started = System.nanoTime();
            try {
                var result = call.get();
                calls.add(new Call(attempt, (System.nanoTime() - started) / 1_000_000, null));
                return result;
            }
            catch (RuntimeException failure) {
                calls.add(new Call(attempt, (System.nanoTime() - started) / 1_000_000,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage()));
                throw failure;
            }
        }

        @Override
        public String providerIdentifier() {
            return delegate.providerIdentifier();
        }

        @Override
        public Optional<String> modelIdentifier() {
            return delegate.modelIdentifier();
        }
    }
}
