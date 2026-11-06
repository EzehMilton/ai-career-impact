package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasureResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevJudgmentService;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevStateMapper;
import com.chikere.futureai.report.alevel.application.ALevelSubjectProfileComposer;
import com.chikere.futureai.trace.AssessmentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

/**
 * Captures the seven {@link ALevelV2CalibrationCases} from Jev through the production {@link ALevelV2JevJudgmentService}
 * (production schema, subject-only state mapper and response mapping) and decides each with the production
 * {@link ALevelDecisionPolicyV2}. It writes a file in the {@link ALevelV2Calibration#load} format with source
 * {@code JEV} and per-case provenance, plus a review report. Test code: calls pass to the client unchanged and only
 * the responses are kept, to read the model and request id Jev returned.
 */
final class ALevelV2LiveCalibrationCapture {

    /** Within this many points of a band boundary, a score is reported as borderline. */
    static final double BORDERLINE_POINTS = 0.5;

    record CaseResult(ALevelV2CalibrationCases.CalibrationCase calibrationCase, AssessmentJudgmentSet judgments,
            String returnedModel, String requestId, ALevelProfileDecisionResult decision) {
    }

    record Capture(Instant capturedAt, String configuredModel, List<CaseResult> cases) {
    }

    private final ALevelJudgmentSchemaV2 schema = new ALevelJudgmentSchemaV2();
    private final ALevelDecisionPolicyV2 policy = new ALevelDecisionPolicyV2();

    Capture capture(TypeSafeClient client, String configuredModel, Clock clock) {
        // Every call goes to the given client unchanged; only the response is kept, for its model and request id.
        List<SystemOneResponse> responses = new ArrayList<>();
        TypeSafeClient recording = mock(TypeSafeClient.class, invocation -> {
            Object result;
            try {
                result = invocation.getMethod().invoke(client, invocation.getArguments());
            }
            catch (java.lang.reflect.InvocationTargetException failure) {
                // A provider failure reaches the production adapter exactly as it was thrown.
                throw failure.getCause();
            }
            if (result instanceof SystemOneResponse response) {
                responses.add(response);
            }
            return result;
        });
        var service = new ALevelV2JevJudgmentService(recording, schema, new ALevelV2JevStateMapper(), configuredModel,
                ProviderCostTracker.disabled(), AssessmentTrace.disabled());
        Instant capturedAt = Instant.now(clock);
        List<CaseResult> results = new ArrayList<>();
        for (var calibrationCase : ALevelV2CalibrationCases.ALL) {
            // As production sends it: the subject combination only, nothing about the student.
            AssessmentJudgmentSet judgments = service.judge(ALevelJudgmentInput.of(calibrationCase.state()));
            SystemOneResponse response = responses.getLast();
            results.add(new CaseResult(calibrationCase, judgments, response.model(), response.requestId(),
                    policy.decide(judgments)));
        }
        return new Capture(capturedAt, configuredModel, results);
    }

    /** The capture in the {@link ALevelV2Calibration#load} format, with provenance and the full answer per case. */
    String json(Capture capture) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("source", "JEV");
        root.put("schemaVersion", schema.version());
        root.put("capturedAt", capture.capturedAt().toString());
        root.put("configuredModel", capture.configuredModel());
        root.put("note", "Live a-level-v2 judgments captured by ALevelV2LiveCalibrationIT through the production "
                + "ALevelV2JevJudgmentService, sending the subject combination only. No credentials are stored.");
        List<Map<String, Object>> cases = new ArrayList<>();
        for (CaseResult result : capture.cases()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", result.calibrationCase().id());
            node.put("subjects", ALevelJudgmentInput.of(result.calibrationCase().state()).subjects());
            node.put("origin", "Live Jev capture " + capture.capturedAt() + ", model " + result.returnedModel()
                    + ", schema " + schema.version() + ", request " + result.requestId());
            node.put("returnedModel", result.returnedModel());
            node.put("requestId", result.requestId());
            node.put("informationSufficiency", judgment(result.judgments(),
                    ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT).numericValue().orElseThrow());
            Map<String, Object> judgments = new LinkedHashMap<>();
            for (String dimension : ALevelDecisionPolicyV2.SCORE_DIMENSIONS) {
                DimensionJudgment judgment = judgment(result.judgments(), dimension);
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("score", judgment.numericValue().orElseThrow());
                value.put("confidence", judgment.confidenceValue().orElseThrow());
                value.put("nearestLabel", judgment.categoricalValue().orElse(null));
                value.put("probabilities", judgment.probabilityDistribution());
                judgments.put(dimension, value);
            }
            node.put("judgments", judgments);
            cases.add(node);
        }
        root.put("cases", cases);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
    }

    /** The review report: raw judgments, the four production scores and bands, and what is borderline. */
    String report(Capture capture) {
        StringBuilder out = new StringBuilder("# A-level v2 live Jev calibration\n\n")
                .append("- Captured: ").append(capture.capturedAt()).append('\n')
                .append("- Schema: ").append(schema.version()).append('\n')
                .append("- Configured model: ").append(capture.configuredModel()).append('\n')
                .append("- Decision policy: ").append(ALevelDecisionPolicyV2.POLICY_VERSION).append(" (unchanged)\n")
                .append("- Borderline: a score within ").append(BORDERLINE_POINTS).append(" of ")
                .append(ALevelDecisionPolicyV2.MODERATE_FROM).append(" or ").append(ALevelDecisionPolicyV2.HIGH_FROM)
                .append(", or a judgment confidence below ").append(ALevelDecisionPolicyV2.LOWER_CONFIDENCE_BELOW)
                .append("\n\n");
        for (CaseResult result : capture.cases()) {
            out.append("## ").append(result.calibrationCase().id()).append("\n\n")
                    .append("Subjects: ").append(String.join(", ",
                            ALevelJudgmentInput.of(result.calibrationCase().state()).subjects())).append("  \n")
                    .append("Returned model: ").append(result.returnedModel()).append(", request ")
                    .append(result.requestId()).append("  \n")
                    .append("Information sufficiency: ").append(format(judgment(result.judgments(),
                            ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT).numericValue().orElseThrow())).append("  \n")
                    .append("Status: ").append(result.decision().assessmentDecision().status()).append("\n\n")
                    .append("| Dimension | Score (0-4) | Confidence | Nearest label |\n|---|---|---|---|\n");
            for (String dimension : ALevelDecisionPolicyV2.SCORE_DIMENSIONS) {
                DimensionJudgment judgment = judgment(result.judgments(), dimension);
                double confidence = judgment.confidenceValue().orElseThrow();
                out.append("| ").append(dimension).append(" | ").append(format(judgment.numericValue().orElseThrow()))
                        .append(" | ").append(format(confidence))
                        .append(confidence < ALevelDecisionPolicyV2.LOWER_CONFIDENCE_BELOW ? " (low)" : "")
                        .append(" | ").append(judgment.categoricalValue().orElse("")).append(" |\n");
            }
            out.append("\n| Measure | Score (0-10) | Band | Customer label | Lower confidence | Borderline |\n")
                    .append("|---|---|---|---|---|---|\n");
            for (ALevelProfileMeasure measure : ALevelProfileMeasure.values()) {
                Optional<ALevelProfileMeasureResult> value = result.decision().measure(measure);
                if (value.isEmpty()) {
                    out.append("| ").append(measure.displayName()).append(" | — | — | — | — | — |\n");
                    continue;
                }
                var measured = value.get();
                out.append("| ").append(measure.displayName()).append(" | ")
                        .append(ALevelSubjectProfileComposer.score(measured.indicator(), measured.band())).append(" | ")
                        .append(measured.band()).append(" | ")
                        .append(ALevelSubjectProfileComposer.bandLabel(measure, measured.band())).append(" | ")
                        .append(measured.lowerConfidence() ? "yes" : "no").append(" | ")
                        .append(borderline(measured.indicator()) ? "yes" : "no").append(" |\n");
            }
            out.append('\n');
        }
        return out.toString();
    }

    static boolean borderline(double indicator) {
        return Math.abs(indicator - ALevelDecisionPolicyV2.MODERATE_FROM) < BORDERLINE_POINTS
                || Math.abs(indicator - ALevelDecisionPolicyV2.HIGH_FROM) < BORDERLINE_POINTS;
    }

    private static DimensionJudgment judgment(AssessmentJudgmentSet set, String dimension) {
        return set.judgments().stream().filter(judgment -> judgment.dimensionId().equals(dimension)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no judgment for " + dimension));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
