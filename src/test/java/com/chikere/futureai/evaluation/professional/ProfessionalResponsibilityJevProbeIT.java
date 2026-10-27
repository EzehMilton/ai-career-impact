package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.typesafe.JevTraceRendering;
import com.chikere.futureai.livesmoke.LiveSmokeCase;
import com.chikere.futureai.livesmoke.LiveSmokeCaseCatalog;
import com.chikere.futureai.livesmoke.LiveSmokeJourney;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityJevProvider;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityStateMapper;
import com.chikere.futureai.trace.AssessmentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 4B live probe: real Jev responsibility analysis for the saved Professional smoke cases at increasing
 * batch sizes, to decide between one call for up to eight responsibilities and two calls of four. Calls Jev only,
 * never OpenAI. Never selected by ordinary test or verify runs.
 *
 * <pre>
 * TYPESAFE_API_KEY=... ./mvnw -Plive-responsibility-probe verify
 *   [-Djev.responsibility.probe.sizes=1,4,8] [-Djev.responsibility.probe.case=registered-nurse]
 * </pre>
 *
 * Writes {@code .live-smoke/probes/responsibility-probe-<time>.md} plus the raw results as JSON, outside target/
 * so a later clean does not delete them.
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "jev.responsibility.probe.enabled", matches = "true")
class ProfessionalResponsibilityJevProbeIT {

    private static final List<String> CASES = List.of("registered-nurse", "java-software-developer",
            "management-accountant", "electrician", "secondary-school-teacher", "solicitor", "customer-service-adviser");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void probesResponsibilityAnalysisAtIncreasingBatchSizes() throws Exception {
        String model = System.getProperty("spring.ai.typesafe.model", "jev-latest");
        TypeSafeClient client = TypeSafeClient.builder().apiKey(System.getenv("TYPESAFE_API_KEY")).defaultModel(model).build();
        var schema = new ProfessionalResponsibilitySchema();
        var stateMapper = new ProfessionalResponsibilityStateMapper();
        var provider = new ProfessionalResponsibilityJevProvider(client, schema, stateMapper, model,
                ProviderCostTracker.disabled(), AssessmentTrace.disabled());
        int[] sizes = Arrays.stream(System.getProperty("jev.responsibility.probe.sizes", "1,4,8").split(","))
                .map(String::strip).mapToInt(Integer::parseInt).toArray();
        String only = System.getProperty("jev.responsibility.probe.case", "");
        var catalog = new LiveSmokeCaseCatalog();

        StringBuilder report = new StringBuilder("# Responsibility analysis live Jev probe\n\n")
                .append("- Schema: `").append(schema.version()).append("`\n- Model requested: `").append(model)
                .append("`\n- Started: ").append(Instant.now()).append("\n- OpenAI: not called\n\n")
                .append("| Case | Responsibilities | Questions | Request bytes (state + questions) | Duration ms | Outcome | "
                        + "Tokens in/out | Distributions on every judgment |\n|---|---:|---:|---:|---:|---|---|---|\n");
        Map<String, Object> raw = new LinkedHashMap<>();
        StringBuilder details = new StringBuilder();
        for (String caseId : CASES) {
            if (!only.isBlank() && !only.equals(caseId)) continue;
            LiveSmokeCase smokeCase = catalog.find(LiveSmokeJourney.PROFESSIONAL, caseId).orElseThrow();
            List<String> all = List.of(((String) smokeCase.fields().get("responsibilities")).split("\n"));
            Map<Integer, ProfessionalResponsibilityJudgmentResult> bySize = new LinkedHashMap<>();
            for (int size : sizes) {
                var request = new ProfessionalResponsibilityAnalysisRequest(UUID.randomUUID(),
                        (String) smokeCase.fields().get("profession"), (String) smokeCase.fields().get("workDescription"),
                        all.subList(0, Math.min(size, all.size())),
                        List.of(((String) smokeCase.fields().get("skills")).split("\n")));
                int questions = schema.questions(request).size();
                long bytes = JSON.writeValueAsBytes(JevTraceRendering.request(stateMapper.map(request),
                        schema.questions(request), schema.version(), model, client)).length;
                long started = System.nanoTime();
                String outcome;
                ProfessionalResponsibilityJudgmentResult result = null;
                try {
                    result = provider.judge(request);
                    outcome = "SUCCEEDED, all " + questions + " answers valid";
                }
                catch (RuntimeException failure) {
                    outcome = "FAILED: " + failure.getMessage();
                }
                long millis = (System.nanoTime() - started) / 1_000_000;
                report.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %s | %s | %s |%n", caseId,
                        request.responsibilities().size(), questions, bytes, millis, outcome.replace("|", "/"),
                        result == null ? "-" : result.inputTokens().orElse(-1) + "/" + result.outputTokens().orElse(-1),
                        result == null ? "-" : everyJudgmentHasADistribution(result)));
                if (result != null) {
                    bySize.put(request.responsibilities().size(), result);
                    raw.put(caseId + "-" + request.responsibilities().size(), result);
                    details.append(table(caseId, result));
                }
            }
            details.append(consistency(caseId, bySize));
        }
        report.append("\n").append(details);
        Path directory = Path.of(".live-smoke", "probes");
        Files.createDirectories(directory);
        String stamp = Instant.now().toString().replace(":", "-");
        Path markdown = directory.resolve("responsibility-probe-" + stamp + ".md");
        Files.writeString(markdown, report.toString(), StandardCharsets.UTF_8);
        Files.write(directory.resolve("responsibility-probe-" + stamp + ".json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(raw));
        System.out.println("Responsibility probe report written to " + markdown.toAbsolutePath());
    }

    private static boolean everyJudgmentHasADistribution(ProfessionalResponsibilityJudgmentResult result) {
        return !result.regulatedContext().distribution().isEmpty() && result.responsibilities().stream()
                .allMatch(set -> judgments(set).stream().noneMatch(judgment -> judgment.distribution().isEmpty()));
    }

    private static String table(String caseId, ProfessionalResponsibilityJudgmentResult result) {
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "## %s, %d responsibilities (model %s, request %s)%n%nRegulated context: %s%n%n"
                        + "| Key | Responsibility | Clear | Assistance | Context | Interaction | Physical | Accountability |%n"
                        + "|---|---|---:|---|---|---|---|---|%n", caseId, result.responsibilities().size(),
                result.model().orElse("?"), result.providerRequestId().orElse("?"), cell(result.regulatedContext())));
        for (ResponsibilityJudgmentSet set : result.responsibilities()) {
            out.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %s | %s | %s | %s | %s |%n", set.responsibilityKey(),
                    set.responsibilityText().replace("|", "/"), set.describedClearly(), cell(set.assistanceScope()),
                    cell(set.contextualJudgement()), cell(set.humanInteraction()), cell(set.physicalPresence()),
                    cell(set.outcomeAccountability())));
        }
        return out.append("\n").toString();
    }

    /** value (confidence) [P0 P1 P2 P3 P4] */
    private static String cell(ResponsibilityScoredJudgment judgment) {
        StringBuilder probabilities = new StringBuilder();
        for (int level = 0; level <= 4; level++) {
            probabilities.append(level == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.2f",
                    judgment.distribution().getOrDefault(level, 0.0)));
        }
        return String.format(Locale.ROOT, "%.2f (%.2f) [%s]", judgment.value(),
                judgment.providerConfidence().orElse(Double.NaN), probabilities);
    }

    /** How far the same responsibility's judgments move between batch sizes: the largest absolute difference. */
    private static String consistency(String caseId, Map<Integer, ProfessionalResponsibilityJudgmentResult> bySize) {
        if (bySize.size() < 2) return "";
        StringBuilder out = new StringBuilder("### " + caseId + ": same responsibility across batch sizes\n\n"
                + "| Key | Judgment | Values by batch size | Largest difference |\n|---|---|---|---:|\n");
        List<Integer> sizes = new ArrayList<>(bySize.keySet());
        int shared = sizes.stream().mapToInt(Integer::intValue).min().orElse(0);
        String[] names = {"described_clearly", "assistance_scope", "contextual_judgement", "human_interaction",
                "physical_presence", "outcome_accountability"};
        for (int index = 0; index < shared; index++) {
            for (int judgment = 0; judgment < names.length; judgment++) {
                List<Double> values = new ArrayList<>();
                for (int size : sizes) values.add(value(bySize.get(size).responsibilities().get(index), judgment));
                double spread = values.stream().mapToDouble(Double::doubleValue).max().orElse(0)
                        - values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                out.append(String.format(Locale.ROOT, "| r%d | %s | %s | %.2f |%n", index + 1, names[judgment],
                        values.stream().map(v -> String.format(Locale.ROOT, "%.2f", v)).toList(), spread));
            }
        }
        return out.append("\n").toString();
    }

    private static double value(ResponsibilityJudgmentSet set, int judgment) {
        return judgment == 0 ? set.describedClearly() : judgments(set).get(judgment - 1).value();
    }

    private static List<ResponsibilityScoredJudgment> judgments(ResponsibilityJudgmentSet set) {
        return List.of(set.assistanceScope(), set.contextualJudgement(), set.humanInteraction(), set.physicalPresence(),
                set.outcomeAccountability());
    }
}
