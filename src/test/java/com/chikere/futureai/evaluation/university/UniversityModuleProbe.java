package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.evaluation.university.UniversityModuleProbeCases.ProbeCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Stage 5C module probe: one module-analysis call per course through the production
 * {@link UniversityModuleJudgmentProvider}, with the raw provider response checked independently of the adapter.
 * It writes results.json, README.md and one capture per course. It never classifies modules.
 */
final class UniversityModuleProbe {

    static final ObjectMapper JSON = new ObjectMapper();
    /** Below this, a module's clarity answer is listed for review. Diagnostic only: nothing is excluded. */
    static final double CLARITY_REVIEW_BELOW = 0.5;
    static final String AI_LEARNING_INVARIANT = "AI learning assistance describes where AI may support learning or "
            + "preparation. It does not determine what use is permitted in assessed work.";

    /** The last raw provider response, taken once per course. */
    @FunctionalInterface
    interface RawResponses {
        RawResponses NONE = Optional::empty;

        Optional<SystemOneResponse> takeResponse();
    }

    record Validation(int expectedAnswers, int receivedAnswers, List<String> missing, List<String> unexpected,
            List<String> invalid, int validModules) {

        int missingOrInvalid() {
            return missing.size() + invalid.size();
        }
    }

    record CourseRun(ProbeCase probeCase, Optional<UniversityModuleJudgmentResult> result,
            Optional<SystemOneResponse> raw, Optional<Validation> validation, int jevCalls, long durationMs,
            Optional<String> error) {
    }

    private final UniversityModuleJudgmentProvider provider;
    private final UniversityModuleSchema schema = new UniversityModuleSchema();
    private final RawResponses rawResponses;

    UniversityModuleProbe(UniversityModuleJudgmentProvider provider, RawResponses rawResponses) {
        this.provider = Objects.requireNonNull(provider);
        this.rawResponses = Objects.requireNonNull(rawResponses);
    }

    List<CourseRun> run(List<ProbeCase> cases) {
        List<CourseRun> runs = new ArrayList<>();
        for (ProbeCase probeCase : cases) {
            var request = probeCase.request();
            long started = System.nanoTime();
            Optional<UniversityModuleJudgmentResult> result = Optional.empty();
            Optional<String> error = Optional.empty();
            try {
                result = Optional.of(provider.judge(request));
            }
            catch (RuntimeException failure) {
                error = Optional.of(describe(failure));
            }
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            Optional<SystemOneResponse> raw = rawResponses.takeResponse();
            Map<String, Question> questions = schema.questions(request);
            runs.add(new CourseRun(probeCase, result, raw, raw.map(response -> validate(questions, response,
                    request.keyedModules().keySet().stream().toList())), 1, durationMs, error));
        }
        return List.copyOf(runs);
    }

    /** Checks the raw answers against the questions asked, independently of the adapter's own mapping. */
    static Validation validate(Map<String, Question> questions, SystemOneResponse response, List<String> moduleKeys) {
        Map<String, Answer> answers = response.answers() == null ? Map.of() : response.answers();
        TreeSet<String> missingNames = new TreeSet<>(questions.keySet());
        missingNames.removeAll(answers.keySet());
        TreeSet<String> unexpectedNames = new TreeSet<>(answers.keySet());
        unexpectedNames.removeAll(questions.keySet());
        List<String> missing = List.copyOf(missingNames);
        List<String> unexpected = List.copyOf(unexpectedNames);
        List<String> invalid = new ArrayList<>();
        for (String name : questions.keySet()) {
            Answer answer = answers.get(name);
            if (answer == null) continue;
            String judgment = name.substring(name.indexOf("__") + 2);
            boolean truth = UniversityModuleSchema.TRUTH_JUDGMENTS.contains(judgment);
            if (truth && !(answer instanceof NoulAnswer noul && noul.value() >= 0 && noul.value() <= 1)) {
                invalid.add(name);
            }
            else if (!truth) {
                if (!(answer instanceof ScoreAnswer score)) {
                    invalid.add(name);
                    continue;
                }
                try {
                    new ModuleScoredJudgment(score.value(), new LinkedHashMap<>(score.probabilities()),
                            java.util.OptionalDouble.of(score.confidence()), java.util.OptionalInt.empty(),
                            Optional.empty());
                }
                catch (RuntimeException rejected) {
                    invalid.add(name + " (" + rejected.getMessage() + ")");
                }
            }
        }
        int valid = (int) moduleKeys.stream().filter(key -> UniversityModuleSchema.JUDGMENTS.stream()
                .map(judgment -> UniversityModuleSchema.answerName(key, judgment))
                .allMatch(name -> answers.containsKey(name) && invalid.stream().noneMatch(bad -> bad.startsWith(name))))
                .count();
        return new Validation(questions.size(), answers.size(), missing, unexpected, invalid, valid);
    }

    static void write(Path directory, String mode, String model, Instant startedAt, List<CourseRun> runs)
            throws IOException {
        Files.createDirectories(directory);
        for (CourseRun run : runs) {
            Files.write(directory.resolve(run.probeCase().baselineCase().id() + ".json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(capture(mode, model, startedAt, run)));
        }
        Files.write(directory.resolve("results.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(results(mode, model, startedAt, runs)));
        Files.writeString(directory.resolve("README.md"), readme(mode, model, startedAt, runs), StandardCharsets.UTF_8);
    }

    static Map<String, Object> capture(String mode, String model, Instant startedAt, CourseRun run) {
        ProbeCase probe = run.probeCase();
        var request = probe.request();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", probe.baselineCase().id());
        out.put("mode", mode);
        out.put("capturedAt", startedAt.toString());
        out.put("schemaVersion", UniversityModuleSchema.VERSION);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("courseTitle", request.courseTitle());
        context.put("courseDescription", request.courseDescription());
        context.put("skillsDeveloped", request.skillsDeveloped());
        context.put("allModules", probe.baselineCase().state().keyModules());
        context.put("preferredCareerAreas", "not sent: career areas must not influence module judgments");
        out.put("courseContext", context);
        out.put("selectedModules", request.keyedModules().entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "title", entry.getValue())).toList());
        out.put("omittedModules", probe.omittedModules());
        out.put("selectionNote", probe.selectionNote() + " Probe selection only, not production behaviour.");
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("configuredModel", model);
        provider.put("jevCalls", run.jevCalls());
        provider.put("durationMs", run.durationMs());
        provider.put("model", run.result().flatMap(UniversityModuleJudgmentResult::model).orElse(null));
        provider.put("requestId", run.result().flatMap(UniversityModuleJudgmentResult::providerRequestId).orElse(null));
        provider.put("inputTokens", tokens(run, true));
        provider.put("outputTokens", tokens(run, false));
        provider.put("error", run.error().orElse(null));
        out.put("provider", provider);
        out.put("validation", run.validation().map(validation -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("adapterStatus", run.result().isPresent() ? "ACCEPTED" : "REJECTED");
            value.put("expectedAnswers", validation.expectedAnswers());
            value.put("receivedAnswers", validation.receivedAnswers());
            value.put("missing", validation.missing());
            value.put("unexpected", validation.unexpected());
            value.put("invalid", validation.invalid());
            value.put("validModules", validation.validModules());
            return value;
        }).orElse(null));
        out.put("aiLearningAssistanceInvariant", AI_LEARNING_INVARIANT);
        out.put("moduleJudgments", run.result().map(result -> result.modules().stream()
                .map(UniversityModuleProbe::module).toList()).orElse(null));
        out.put("rawProviderResponse", run.raw().map(RecordingTypeSafeClient::render).orElse(null));
        return out;
    }

    private static Map<String, Object> module(UniversityModuleJudgmentSet set) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", set.moduleKey());
        out.put("title", set.moduleTitle());
        out.put("described_clearly", set.describedClearly());
        out.put("professional_standards", set.professionalStandards());
        out.put("ai_learning_assistance", scored(set.aiLearningAssistance()));
        out.put("independent_demonstration", scored(set.independentDemonstration()));
        out.put("human_judgement", scored(set.humanJudgement()));
        out.put("interpersonal_work", scored(set.interpersonalWork()));
        out.put("practical_application", scored(set.practicalApplication()));
        out.put("technical_quantitative", scored(set.technicalQuantitative()));
        return out;
    }

    private static Map<String, Object> scored(ModuleScoredJudgment judgment) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("value", judgment.value());
        out.put("confidence", judgment.providerConfidence().isPresent() ? judgment.providerConfidence().getAsDouble()
                : null);
        out.put("nearestLevel", judgment.nearestLevel().isPresent() ? judgment.nearestLevel().getAsInt() : null);
        out.put("distribution", judgment.distribution());
        return out;
    }

    static Map<String, Object> results(String mode, String model, Instant startedAt, List<CourseRun> runs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", "5C university module probe");
        out.put("mode", mode);
        out.put("startedAt", startedAt.toString());
        out.put("configuredModel", model);
        out.put("schemaVersion", UniversityModuleSchema.VERSION);
        out.put("totalJevCalls", runs.stream().mapToInt(CourseRun::jevCalls).sum());
        out.put("courses", runs.stream().map(run -> {
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("caseId", run.probeCase().baselineCase().id());
            course.put("course", run.probeCase().baselineCase().state().courseTitle());
            course.put("selectedModuleCount", run.probeCase().selectedModules().size());
            course.put("jevCalls", run.jevCalls());
            course.put("durationMs", run.durationMs());
            course.put("inputTokens", tokens(run, true));
            course.put("outputTokens", tokens(run, false));
            course.put("providerError", run.error().orElse(null));
            course.put("schemaVersion", UniversityModuleSchema.VERSION);
            course.put("validModules", run.validation().map(Validation::validModules).orElse(0));
            course.put("missingOrInvalidJudgments", run.validation().map(Validation::missingOrInvalid).orElse(null));
            course.put("unexpectedAnswers", run.validation().map(validation -> validation.unexpected().size())
                    .orElse(null));
            course.put("describedClearly", run.result().map(UniversityModuleProbe::claritySummary).orElse(null));
            return course;
        }).toList());
        return out;
    }

    private static Map<String, Object> claritySummary(UniversityModuleJudgmentResult result) {
        double[] values = result.modules().stream().mapToDouble(UniversityModuleJudgmentSet::describedClearly).toArray();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("min", java.util.Arrays.stream(values).min().orElse(Double.NaN));
        out.put("mean", java.util.Arrays.stream(values).average().orElse(Double.NaN));
        out.put("max", java.util.Arrays.stream(values).max().orElse(Double.NaN));
        out.put("below" + CLARITY_REVIEW_BELOW, result.modules().stream()
                .filter(set -> set.describedClearly() < CLARITY_REVIEW_BELOW)
                .map(set -> set.moduleKey() + " " + set.moduleTitle()).toList());
        return out;
    }

    static String readme(String mode, String model, Instant startedAt, List<CourseRun> runs) {
        StringBuilder out = new StringBuilder("# University module probe (Stage 5C)\n\n")
                .append("- Mode: `").append(mode).append("`")
                .append(mode.equals("LIVE") ? "" : " (synthetic answers: plumbing check only, not evidence)")
                .append("\n- Started: ").append(startedAt)
                .append("\n- Schema: `").append(UniversityModuleSchema.VERSION).append("`; configured Jev model: `")
                .append(model).append("`")
                .append("\n- Jev calls: ").append(runs.stream().mapToInt(CourseRun::jevCalls).sum())
                .append(" (one call per course, up to eight modules)")
                .append("\n- OpenAI, Stripe, persistence, classification and guidance: not used")
                .append("\n- Module selection is a probe convenience; the product will ask the student to choose.")
                .append("\n\n> ").append(AI_LEARNING_INVARIANT).append("\n\n")
                .append("## Calls\n\n| Course | Modules | Duration ms | Tokens in/out | Adapter | Expected/received "
                        + "answers | Missing or invalid | Unexpected | Error |\n|---|---:|---:|---|---|---|---:|---:|---|\n");
        for (CourseRun run : runs) {
            var validation = run.validation();
            out.append("| ").append(run.probeCase().baselineCase().id()).append(" | ")
                    .append(run.probeCase().selectedModules().size()).append(" | ").append(run.durationMs()).append(" | ")
                    .append(tokens(run, true)).append('/').append(tokens(run, false)).append(" | ")
                    .append(run.result().isPresent() ? "ACCEPTED" : "REJECTED").append(" | ")
                    .append(validation.map(value -> value.expectedAnswers() + "/" + value.receivedAnswers()).orElse("—"))
                    .append(" | ").append(validation.map(value -> String.valueOf(value.missingOrInvalid())).orElse("—"))
                    .append(" | ").append(validation.map(value -> String.valueOf(value.unexpected().size())).orElse("—"))
                    .append(" | ").append(run.error().orElse("").replace("|", "/")).append(" |\n");
        }
        out.append("\nCells are value (confidence). Clarity and professional standards are 0–1 truth values; the "
                + "others are 0–4.\n");
        for (CourseRun run : runs) {
            out.append("\n## ").append(run.probeCase().baselineCase().state().courseTitle()).append("\n\n")
                    .append("Selected: ").append(String.join("; ", run.probeCase().selectedModules()))
                    .append(".\nOmitted: ").append(run.probeCase().omittedModules().isEmpty() ? "none"
                            : String.join("; ", run.probeCase().omittedModules())).append(".\n\n");
            if (run.result().isEmpty()) {
                out.append("No accepted judgments: ").append(run.error().orElse("unknown")).append("\n");
                run.validation().ifPresent(validation -> out.append("Missing: ").append(validation.missing())
                        .append("; unexpected: ").append(validation.unexpected()).append("; invalid: ")
                        .append(validation.invalid()).append("\n"));
                continue;
            }
            out.append("| Id | Module | Clear | Prof. standards | AI learning | Independent | Judgement | "
                    + "Interpersonal | Practical | Technical |\n|---|---|---:|---:|---|---|---|---|---|---|\n");
            for (var set : run.result().orElseThrow().modules()) {
                out.append("| ").append(set.moduleKey()).append(" | ").append(set.moduleTitle().replace("|", "/"))
                        .append(" | ").append(f(set.describedClearly())).append(" | ")
                        .append(f(set.professionalStandards())).append(" | ").append(cell(set.aiLearningAssistance()))
                        .append(" | ").append(cell(set.independentDemonstration())).append(" | ")
                        .append(cell(set.humanJudgement())).append(" | ").append(cell(set.interpersonalWork()))
                        .append(" | ").append(cell(set.practicalApplication())).append(" | ")
                        .append(cell(set.technicalQuantitative())).append(" |\n");
            }
        }
        List<UniversityModuleJudgmentSet> all = runs.stream().flatMap(run -> run.result().stream())
                .flatMap(result -> result.modules().stream()).toList();
        if (!all.isEmpty()) {
            out.append("\n## Patterns across all accepted modules (").append(all.size()).append(")\n\n")
                    .append("| Judgment | Min | Mean | Max |\n|---|---:|---:|---:|\n");
            Map<String, Function<UniversityModuleJudgmentSet, Double>> judgments = new LinkedHashMap<>();
            judgments.put("described_clearly", UniversityModuleJudgmentSet::describedClearly);
            judgments.put("professional_standards", UniversityModuleJudgmentSet::professionalStandards);
            judgments.put("ai_learning_assistance", set -> set.aiLearningAssistance().value());
            judgments.put("independent_demonstration", set -> set.independentDemonstration().value());
            judgments.put("human_judgement", set -> set.humanJudgement().value());
            judgments.put("interpersonal_work", set -> set.interpersonalWork().value());
            judgments.put("practical_application", set -> set.practicalApplication().value());
            judgments.put("technical_quantitative", set -> set.technicalQuantitative().value());
            judgments.forEach((name, of) -> {
                double[] values = all.stream().mapToDouble(of::apply).toArray();
                out.append("| ").append(name).append(" | ").append(f(java.util.Arrays.stream(values).min().orElseThrow()))
                        .append(" | ").append(f(java.util.Arrays.stream(values).average().orElseThrow())).append(" | ")
                        .append(f(java.util.Arrays.stream(values).max().orElseThrow())).append(" |\n");
            });
            out.append("\n### Clarity below ").append(f(CLARITY_REVIEW_BELOW)).append(" (review, not exclusion)\n\n");
            List<String> unclear = runs.stream().flatMap(run -> run.result().stream().flatMap(result -> result.modules()
                    .stream().filter(set -> set.describedClearly() < CLARITY_REVIEW_BELOW)
                    .map(set -> "- " + run.probeCase().baselineCase().id() + " " + set.moduleKey() + " "
                            + set.moduleTitle() + ": " + f(set.describedClearly())))).toList();
            out.append(unclear.isEmpty() ? "- none\n" : String.join("\n", unclear) + "\n");
        }
        return out.toString();
    }

    private static Object tokens(CourseRun run, boolean input) {
        return run.result().map(result -> input ? result.inputTokens() : result.outputTokens())
                .filter(java.util.OptionalLong::isPresent).map(value -> (Object) value.getAsLong())
                .orElseGet(() -> run.raw().map(SystemOneResponse::usage).map(usage -> (Object) (input
                        ? usage.inputTokens() : usage.outputTokens())).orElse("—"));
    }

    private static String cell(ModuleScoredJudgment judgment) {
        return f(judgment.value()) + (judgment.providerConfidence().isPresent()
                ? " (" + f(judgment.providerConfidence().getAsDouble()) + ")" : "");
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String describe(RuntimeException failure) {
        String message = Optional.ofNullable(failure.getMessage()).orElse("");
        if (message.length() > 400) message = message.substring(0, 400) + "…";
        return failure.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }
}
