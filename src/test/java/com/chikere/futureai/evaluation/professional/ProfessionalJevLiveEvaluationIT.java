package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "jev.live.evaluation.enabled", matches = "true")
class ProfessionalJevLiveEvaluationIT {

    private static final Path REPORT_PATH =
            Path.of("target", "jev-evaluation", "professional-v1-report.md");

    @Test
    void evaluatesProfessionalV1SequentiallyAndWritesReport() {
        List<ProfessionalEvaluationCase> allCases = ProfessionalEvaluationDataset.load();
        List<ProfessionalEvaluationCase> selectedCases = ProfessionalEvaluationDataset.select(
                allCases, System.getProperty("jev.eval.case"));

        TypeSafeClient typeSafeClient = TypeSafeClient.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .build();
        ProfessionalJevJudgmentService judgmentService = new ProfessionalJevJudgmentService(
                typeSafeClient,
                new ProfessionalJudgmentSchema(),
                new ProfessionalJevStateMapper());
        ProfessionalDecisionPolicyV1 decisionPolicy = new ProfessionalDecisionPolicyV1();

        List<ProfessionalEvaluationResult> results = new ArrayList<>();
        for (ProfessionalEvaluationCase evaluationCase : selectedCases) {
            AssessmentJudgmentSet judgments = judgmentService.judge(evaluationCase.toAssessmentState());
            ProfessionalDecisionResult decision = decisionPolicy.decide(judgments);
            results.add(new ProfessionalEvaluationResult(evaluationCase, judgments, decision));
        }

        ProfessionalEvaluationReportWriter.write(results, REPORT_PATH);
        System.out.println("Professional Jev evaluation report written to " + REPORT_PATH.toAbsolutePath());
    }
}
