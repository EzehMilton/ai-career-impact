package com.chikere.futureai.coursemodule.university.typesafe;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentException;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.answerName;

/**
 * Module analysis through one Jev System One call for up to eight modules. This call strategy is locked by the
 * Stage 5C live probe, in which every call returned all 64 expected answers; there is no batching fallback.
 *
 * <p>Mapping is strict: the response must answer exactly the questions asked, each with the expected answer type
 * and a valid value and distribution, and the mapped result must name exactly the requested modules in order. A
 * missing, unexpected or invalid answer fails the call; nothing is filled in, dropped or reordered.</p>
 */
public final class UniversityModuleJevProvider implements UniversityModuleJudgmentProvider {

    private final TypeSafeClient typeSafeClient;
    private final UniversityModuleSchema schema;
    private final UniversityModuleStateMapper stateMapper;
    private final String configuredModelIdentifier;
    private final ProviderCostTracker costs;

    public UniversityModuleJevProvider(TypeSafeClient typeSafeClient, UniversityModuleSchema schema,
            UniversityModuleStateMapper stateMapper, String configuredModelIdentifier, ProviderCostTracker costs) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
    }

    @Override
    public UniversityModuleJudgmentResult judge(UniversityModuleAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> state = stateMapper.map(request);
        Map<String, Question> questions = schema.questions(request);
        SystemOneResponse response;
        try {
            response = typeSafeClient.systemOne(state, questions);
        }
        catch (RuntimeException failure) {
            throw new UniversityModuleJudgmentException(UniversityModuleJudgmentException.Kind.PROVIDER_CALL,
                    "the module-analysis provider call failed: " + failure.getClass().getSimpleName(), failure);
        }
        recordUsage(response);
        try {
            return map(request, questions, response);
        }
        catch (IllegalArgumentException invalid) {
            throw new UniversityModuleJudgmentException("invalid module judgment: " + invalid.getMessage(), invalid);
        }
    }

    @Override
    public UniversityModuleJudgmentSource source() {
        return UniversityModuleJudgmentSource.JEV;
    }

    private UniversityModuleJudgmentResult map(UniversityModuleAnalysisRequest request, Map<String, Question> questions,
            SystemOneResponse response) {
        if (response == null || response.answers() == null) {
            throw new UniversityModuleJudgmentException("the provider returned no answers");
        }
        Set<String> missing = new TreeSet<>(questions.keySet());
        missing.removeAll(response.answers().keySet());
        Set<String> unexpected = new TreeSet<>(response.answers().keySet());
        unexpected.removeAll(questions.keySet());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new UniversityModuleJudgmentException("the provider answers did not match the questions asked: "
                    + "missing " + missing + ", unexpected " + unexpected);
        }
        List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, String> module : request.keyedModules().entrySet()) {
            String key = module.getKey();
            sets.add(new UniversityModuleJudgmentSet(index++, key, module.getValue(),
                    truth(response, answerName(key, UniversityModuleSchema.DESCRIBED_CLEARLY)),
                    truth(response, answerName(key, UniversityModuleSchema.PROFESSIONAL_STANDARDS)),
                    scored(response, answerName(key, UniversityModuleSchema.AI_LEARNING_ASSISTANCE)),
                    scored(response, answerName(key, UniversityModuleSchema.INDEPENDENT_DEMONSTRATION)),
                    scored(response, answerName(key, UniversityModuleSchema.HUMAN_JUDGEMENT)),
                    scored(response, answerName(key, UniversityModuleSchema.INTERPERSONAL_WORK)),
                    scored(response, answerName(key, UniversityModuleSchema.PRACTICAL_APPLICATION)),
                    scored(response, answerName(key, UniversityModuleSchema.TECHNICAL_QUANTITATIVE))));
        }
        var usage = response.usage();
        String model = response.model() == null || response.model().isBlank() ? configuredModelIdentifier
                : response.model();
        UniversityModuleJudgmentResult result = new UniversityModuleJudgmentResult(schema.version(),
                UniversityModuleJudgmentSource.JEV, Optional.ofNullable(model),
                Optional.ofNullable(response.requestId()),
                usage == null || usage.inputTokens() == null ? OptionalLong.empty()
                        : OptionalLong.of(usage.inputTokens()),
                usage == null || usage.outputTokens() == null ? OptionalLong.empty()
                        : OptionalLong.of(usage.outputTokens()),
                sets);
        if (!result.answers(request)) {
            throw new UniversityModuleJudgmentException("the mapped result does not correspond to the request");
        }
        return result;
    }

    private static ModuleScoredJudgment scored(SystemOneResponse response, String name) {
        Answer answer = response.answers().get(name);
        if (!(answer instanceof ScoreAnswer score)) {
            throw new UniversityModuleJudgmentException("answer " + name + " must be a score, was "
                    + (answer == null ? "absent" : answer.getClass().getSimpleName()));
        }
        try {
            int nearest = score.nearestLevel();
            return new ModuleScoredJudgment(score.value(), new LinkedHashMap<>(score.probabilities()),
                    OptionalDouble.of(score.confidence()), nearest < 0 ? OptionalInt.empty() : OptionalInt.of(nearest),
                    Optional.ofNullable(score.nearestLabel()));
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new UniversityModuleJudgmentException("answer " + name + " is invalid: " + invalid.getMessage(),
                    invalid);
        }
    }

    private static double truth(SystemOneResponse response, String name) {
        Answer answer = response.answers().get(name);
        if (!(answer instanceof NoulAnswer noul)) {
            throw new UniversityModuleJudgmentException("answer " + name + " must be a truth value, was "
                    + (answer == null ? "absent" : answer.getClass().getSimpleName()));
        }
        double value = noul.value();
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new UniversityModuleJudgmentException("answer " + name + " must be between 0 and 1");
        }
        return value;
    }

    private void recordUsage(SystemOneResponse response) {
        var usage = response == null ? null : response.usage();
        String model = response == null || response.model() == null || response.model().isBlank()
                ? configuredModelIdentifier : response.model();
        costs.record(ProviderUsage.of(ProviderUsage.JEV, model,
                usage == null ? null : usage.inputTokens(), usage == null ? null : usage.outputTokens()));
    }
}
