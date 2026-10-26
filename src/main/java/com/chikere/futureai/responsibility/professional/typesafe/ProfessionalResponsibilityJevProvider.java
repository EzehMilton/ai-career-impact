package com.chikere.futureai.responsibility.professional.typesafe;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.judgment.typesafe.JevTraceRendering;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentException;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.ArrayList;
import java.util.HashSet;
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

/**
 * Responsibility analysis through one Jev System One call for up to eight responsibilities.
 *
 * <p>Mapping is strict: the response must answer exactly the questions asked, each with the expected answer type
 * and a valid value and distribution. A missing, unexpected or invalid answer fails the call; nothing is filled
 * in or dropped.</p>
 */
public final class ProfessionalResponsibilityJevProvider implements ProfessionalResponsibilityJudgmentProvider {

    private final TypeSafeClient typeSafeClient;
    private final ProfessionalResponsibilitySchema schema;
    private final ProfessionalResponsibilityStateMapper stateMapper;
    private final String configuredModelIdentifier;
    private final ProviderCostTracker costs;
    private final AssessmentTrace trace;

    public ProfessionalResponsibilityJevProvider(TypeSafeClient typeSafeClient, ProfessionalResponsibilitySchema schema,
            ProfessionalResponsibilityStateMapper stateMapper, String configuredModelIdentifier,
            ProviderCostTracker costs, AssessmentTrace trace) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    @Override
    public ProfessionalResponsibilityJudgmentResult judge(ProfessionalResponsibilityAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> state = stateMapper.map(request);
        Map<String, Question> questions = schema.questions(request);
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_ANALYSIS_REQUEST, () -> requestSummary(request,
                questions.size()));
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_MODEL_PROVIDER_REQUEST,
                () -> JevTraceRendering.request(state, questions, schema.version(), configuredModelIdentifier,
                        typeSafeClient));
        SystemOneResponse response;
        try {
            response = trace.timed(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_MODEL_PROVIDER_RESPONSE,
                    "responsibilityModelMs", () -> typeSafeClient.systemOne(state, questions),
                    JevTraceRendering::response);
        }
        catch (RuntimeException failure) {
            traceRejected(failure);
            throw new ResponsibilityJudgmentException("the responsibility-analysis provider call failed: "
                    + failure.getClass().getSimpleName(), failure);
        }
        recordUsage(response);
        ProfessionalResponsibilityJudgmentResult result;
        try {
            result = map(request, questions, response);
        }
        catch (ResponsibilityJudgmentException | IllegalArgumentException invalid) {
            traceRejected(invalid);
            throw invalid instanceof ResponsibilityJudgmentException rejected ? rejected
                    : new ResponsibilityJudgmentException("invalid responsibility judgment: " + invalid.getMessage(),
                            invalid);
        }
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_ANALYSIS_RESULT, () -> Map.of(
                "status", "ACCEPTED", "schemaVersion", result.schemaVersion(),
                "responsibilityCount", result.responsibilities().size(), "result", result));
        return result;
    }

    @Override
    public ResponsibilityJudgmentSource source() {
        return ResponsibilityJudgmentSource.JEV;
    }

    private ProfessionalResponsibilityJudgmentResult map(ProfessionalResponsibilityAnalysisRequest request,
            Map<String, Question> questions, SystemOneResponse response) {
        if (response == null) {
            throw new ResponsibilityJudgmentException("the provider returned no response");
        }
        Set<String> expected = questions.keySet();
        Set<String> received = response.answers().keySet();
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(received);
        Set<String> unexpected = new TreeSet<>(received);
        unexpected.removeAll(expected);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new ResponsibilityJudgmentException("the provider answers did not match the questions asked: "
                    + "missing " + missing + ", unexpected " + unexpected);
        }
        ResponsibilityScoredJudgment regulated = scored(response, ProfessionalResponsibilitySchema.REGULATED_CONTEXT);
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (Map.Entry<String, String> responsibility : request.keyedResponsibilities().entrySet()) {
            String key = responsibility.getKey();
            if (!seen.add(key)) {
                throw new ResponsibilityJudgmentException("duplicate responsibility key " + key);
            }
            sets.add(new ResponsibilityJudgmentSet(index++, key, responsibility.getValue(),
                    clarity(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.DESCRIBED_CLEARLY)),
                    scored(response, key, ProfessionalResponsibilitySchema.ASSISTANCE_SCOPE),
                    scored(response, key, ProfessionalResponsibilitySchema.CONTEXTUAL_JUDGEMENT),
                    scored(response, key, ProfessionalResponsibilitySchema.HUMAN_INTERACTION),
                    scored(response, key, ProfessionalResponsibilitySchema.PHYSICAL_PRESENCE),
                    scored(response, key, ProfessionalResponsibilitySchema.OUTCOME_ACCOUNTABILITY)));
        }
        var usage = response.usage();
        String model = response.model() == null || response.model().isBlank() ? configuredModelIdentifier : response.model();
        ProfessionalResponsibilityJudgmentResult result = new ProfessionalResponsibilityJudgmentResult(schema.version(),
                ResponsibilityJudgmentSource.JEV, Optional.ofNullable(model), Optional.ofNullable(response.requestId()),
                usage == null || usage.inputTokens() == null ? OptionalLong.empty() : OptionalLong.of(usage.inputTokens()),
                usage == null || usage.outputTokens() == null ? OptionalLong.empty() : OptionalLong.of(usage.outputTokens()),
                regulated, sets);
        if (!result.answers(request)) {
            throw new ResponsibilityJudgmentException("the mapped result does not correspond to the request");
        }
        return result;
    }

    private static ResponsibilityScoredJudgment scored(SystemOneResponse response, String key, String judgment) {
        return scored(response, ProfessionalResponsibilitySchema.answerName(key, judgment));
    }

    private static ResponsibilityScoredJudgment scored(SystemOneResponse response, String name) {
        Answer answer = response.answers().get(name);
        if (!(answer instanceof ScoreAnswer score)) {
            throw new ResponsibilityJudgmentException("answer " + name + " must be a score, was "
                    + (answer == null ? "absent" : answer.getClass().getSimpleName()));
        }
        Map<Integer, Double> distribution = new LinkedHashMap<>(score.probabilities());
        int nearest = score.nearestLevel();
        try {
            return new ResponsibilityScoredJudgment(score.value(), distribution, OptionalDouble.of(score.confidence()),
                    nearest < 0 ? OptionalInt.empty() : OptionalInt.of(nearest),
                    Optional.ofNullable(score.nearestLabel()));
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ResponsibilityJudgmentException("answer " + name + " is invalid: " + invalid.getMessage(), invalid);
        }
    }

    private static double clarity(SystemOneResponse response, String name) {
        Answer answer = response.answers().get(name);
        if (!(answer instanceof NoulAnswer noul)) {
            throw new ResponsibilityJudgmentException("answer " + name + " must be a truth value, was "
                    + (answer == null ? "absent" : answer.getClass().getSimpleName()));
        }
        double value = noul.value();
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new ResponsibilityJudgmentException("answer " + name + " must be between 0 and 1");
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

    private Map<String, Object> requestSummary(ProfessionalResponsibilityAnalysisRequest request, int questionCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schema.version());
        payload.put("assessmentId", request.assessmentId());
        payload.put("responsibilityCount", request.responsibilities().size());
        payload.put("questionCount", questionCount);
        payload.put("responsibilities", request.keyedResponsibilities());
        payload.put("provider", "JEV");
        payload.put("configuredModel", configuredModelIdentifier);
        return payload;
    }

    private void traceRejected(Throwable failure) {
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_ANALYSIS_RESULT, () -> Map.of(
                "status", "REJECTED", "errorType", failure.getClass().getName(),
                "errorMessage", AssessmentTrace.safeMessage(failure)));
    }
}
