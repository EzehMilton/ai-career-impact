package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.openai.professional.ProfessionalOpenAiJudgmentService;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiDecisionOptions;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit two-provider calibration; never selected by the normal offline build. */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "professional.decision.calibration.live.enabled", matches = "true")
class ProfessionalDecisionProviderCalibrationLiveIT {

    private static final Path REPORT = Path.of("target", "professional-decision-calibration",
            "professional-v1-jev-openai.md");

    @Test
    void comparesJevAndOpenAiThroughTheUnchangedDeterministicPolicy() {
        String datasetPath = System.getProperty("professional.calibration.dataset");
        List<ProfessionalEvaluationCase> allCases = datasetPath == null || datasetPath.isBlank()
                ? ProfessionalEvaluationDataset.load()
                : ProfessionalEvaluationDataset.load(Path.of(datasetPath));
        List<ProfessionalEvaluationCase> cases = ProfessionalEvaluationDataset.select(
                allCases, System.getProperty("professional.calibration.case"));
        var jev = new ProfessionalJevJudgmentService(TypeSafeClient.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY")).build(),
                new com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema(),
                new ProfessionalJevStateMapper());

        OpenAiDecisionOptions.Builder options = OpenAiDecisionOptions.builder();
        options.apiKey(System.getenv("OPENAI_API_KEY"));
        options.model("gpt-6-luna");
        options.timeout(Duration.ofSeconds(60));
        options.maxRetries(0);
        var openAi = new ProfessionalOpenAiJudgmentService(
                OpenAiDecisionModel.builder().options(options.build()).build(),
                new ProfessionalJudgmentSchema(), new ProfessionalJudgmentStateMapper(), "gpt-6-luna");
        var policy = new ProfessionalDecisionPolicyV2();

        List<ProfessionalEvaluationResult> jevResults = new ArrayList<>();
        List<ProfessionalEvaluationResult> openAiResults = new ArrayList<>();
        for (ProfessionalEvaluationCase calibrationCase : cases) {
            var state = calibrationCase.toAssessmentState();
            var jevJudgments = jev.judge(state);
            jevResults.add(new ProfessionalEvaluationResult(calibrationCase, jevJudgments,
                    policy.decide(jevJudgments)));
            var openAiJudgments = openAi.judge(state);
            openAiResults.add(new ProfessionalEvaluationResult(calibrationCase, openAiJudgments,
                    policy.decide(openAiJudgments)));
        }

        var comparisons = new ProfessionalCalibrationComparison().compare(jevResults, openAiResults);
        ProfessionalCalibrationReportWriter.write(comparisons, REPORT);
        assertThat(comparisons).hasSize(cases.size());
        System.out.println("Professional Jev/OpenAI calibration report written to " + REPORT.toAbsolutePath());
    }
}
