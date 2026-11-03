package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Explicit live evaluation of professional-paid-narrative-v2 against REAL OpenAI for the seven calibration roles.
 * Never selected by normal Surefire or verify: run only with
 * {@code ./mvnw -Plive-paid-narrative verify} and OPENAI_API_KEY set.
 *
 * <p>Each role runs the real orchestration through {@link PaidNarrativeHarness}: a stored assessment, the real
 * complimentary grant freezing the v2 report, and the captured seven-role live Jev analysis as the stored snapshot (Jev
 * is NOT called again: the provider is a mock that must stay untouched). Guidance, the paid narrative with real
 * validation and repair, report-v4 assembly and the v6 PDF are all real. Results are written to
 * {@code .live-smoke/paid-narrative/<timestamp>/}. With {@code -Dopenai.paid.narrative.live.dry-run=true} the same
 * run uses an offline generator and writes to {@code target/paid-narrative-dry-run/}.</p>
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "openai.paid.narrative.live.enabled", matches = "true")
class ProfessionalPaidNarrativeLiveIT {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    /** Roles whose four measures come from a local placeholder fixture, as {@link DetailedReportFixtures} explains. */
    private static final java.util.Set<String> PLACEHOLDER_ROLE_SCORES = java.util.Set.of("electrician-8",
            "secondary-school-teacher-8", "solicitor-8", "customer-service-adviser-8");

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
                    if (dryRun) {
                        // The whole harness with an offline generator: no OpenAI call, output under target/.
                        run(new Offline(), "offline-dry-run", Path.of("target", "paid-narrative-dry-run"));
                    }
                    else {
                        run(generator, model, Path.of(".live-smoke", "paid-narrative"));
                    }
                });
    }

    private void run(ProfessionalPaidNarrativeGenerator generator, String model, Path root) throws Exception {
        Path directory = root.resolve(Instant.now().toString().replace(':', '-'));
        Files.createDirectories(directory);
        List<Map<String, Object>> results = new ArrayList<>();
        StringBuilder markdown = new StringBuilder("# Paid narrative live run\n\nModel: " + model + "\n\n");
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var recorder = new Recording(generator);
            var harness = new PaidNarrativeHarness(recorder);
            UUID id = harness.seed(run);
            var analysis = AnalysisSnapshots.live(run);
            assertThat(harness.assessments.findById(id)).as(run + " assessment stored before preparation").isPresent();
            var service = harness.service;

            long started = System.nanoTime();
            var outcome = service.prepare(id, Optional.empty());
            long preparationMs = (System.nanoTime() - started) / 1_000_000;
            verifyNoInteractions(harness.jev);
            assertThat(recorder.calls).as(run + " reached the paid narrative generator").isNotEmpty();
            assertThat(harness.guidancePolicy.calls).as(run + " guidance decided once").isEqualTo(1);

            var views = new ProfessionalDetailedReportViewMapper();
            var view = outcome.snapshot().map(views::toView).orElseGet(() -> views.toProvisionalView(outcome.report()));
            long pdfStarted = System.nanoTime();
            byte[] pdf = new OpenHtmlProfessionalDetailedReportPdfRenderer().render(view);
            long pdfMs = (System.nanoTime() - pdfStarted) / 1_000_000;
            Files.write(directory.resolve(run + ".pdf"), pdf);
            Files.writeString(directory.resolve(run + ".md"), ProfessionalDetailedReportPdfCalibrationTest.markdown(view)
                    + narrativeMarkdown(outcome.report()), StandardCharsets.UTF_8);

            // The deterministic content must be exactly the stored guidance, whatever the narrative did.
            var expected = new ProfessionalGuidancePolicyV1().decide(analysis);
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
            result.put("roleLevelScores", PLACEHOLDER_ROLE_SCORES.contains(run)
                    ? "PLACEHOLDER (narrative-only; not calibration evidence)" : "CAPTURED");
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
        out.append("\n### Opportunity insights\n");
        report.opportunities().items().forEach(o -> out.append("- ").append(o.responsibility()).append(": ")
                .append(o.explanation()).append("\n  - Uses: ").append(String.join(" | ", o.practicalAiUses()))
                .append("\n  - Yours: ").append(String.join(" | ", o.humanOwnership()))
                .append("\n  - Experiment: ").append(o.firstExperiment()).append("\n"));
        out.append("\n### Human-value insights\n");
        report.humanValue().items().forEach(h -> out.append("- ").append(h.responsibility()).append(": ")
                .append(h.explanation()).append("\n  - Strengthen: ").append(h.strengthen()).append("\n"));
        out.append("\n### Skills\n");
        report.skills().items().forEach(s -> out.append("- ").append(s.label()).append(": ").append(s.explanation())
                .append("\n  - In your work: ").append(String.join(" | ", s.inYourWork())).append("\n"));
        out.append("\n### Plan\n").append(report.plan().narrative()).append("\n");
        report.plan().phases().forEach(p -> p.actions().forEach(a -> out.append("- ").append(a.title()).append(" -> ")
                .append(a.note()).append("\n")));
        var strategy = report.adaptationStrategy();
        out.append("\n### Adaptation strategy (personalised=").append(strategy.personalised()).append(")\n")
                .append("- Protect: ").append(String.join(" | ", strategy.protect())).append("\n")
                .append("- Learn: ").append(String.join(" | ", strategy.learn())).append("\n")
                .append("- Experiment: ").append(String.join(" | ", strategy.experiment())).append("\n")
                .append("- Strengthen: ").append(String.join(" | ", strategy.strengthen())).append("\n")
                .append("- Monitor: ").append(String.join(" | ", strategy.monitor())).append("\n");
        out.append("\n### Closing\n").append(report.closingPerspective()).append("\n");
        return out.toString();
    }

    /**
     * The dry run's generator, never a network call: the calibrated fixture for the Registered Nurse and Management
     * Accountant chains, otherwise valid hand-written copy for exactly the context's ids.
     */
    static final class Offline implements ProfessionalPaidNarrativeGenerator {
        @Override
        public GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context) {
            var narrative = java.util.stream.Stream.of(PaidNarrativeCalibrationFixtures.NURSE,
                            PaidNarrativeCalibrationFixtures.ACCOUNTANT)
                    .filter(run -> AnalysisSnapshots.live(run).assessmentId().equals(context.assessmentId())).findFirst()
                    .flatMap(PaidNarrativeCalibrationFixtures::forRun)
                    .orElseGet(() -> DetailedReportFixtures.validNarrative(context));
            return new GeneratedPaidNarrative(narrative, Optional.empty(), Optional.empty());
        }

        @Override
        public GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback) {
            return generate(context);
        }

        @Override
        public String providerIdentifier() {
            return "offline-dry-run";
        }

        @Override
        public Optional<String> modelIdentifier() {
            return Optional.empty();
        }
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
