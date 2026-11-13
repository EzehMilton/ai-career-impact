package com.chikere.futureai.judgment.openai.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.openai.OpenAiAssessmentJudgmentAdapter;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevStateMapper;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.ai.decision.DecisionModel;

import java.util.Optional;

public final class ALevelV2OpenAiJudgmentService implements ALevelJudgmentProvider {
    private final OpenAiAssessmentJudgmentAdapter<ALevelJudgmentInput> adapter;
    private final String model;

    public ALevelV2OpenAiJudgmentService(DecisionModel decisionModel, ALevelJudgmentSchemaV2 schema,
            ALevelV2JevStateMapper stateMapper, String model, ProviderCostTracker costs, AssessmentTrace trace) {
        this.model = model;
        this.adapter = new OpenAiAssessmentJudgmentAdapter<>(decisionModel, JourneyType.A_LEVEL, schema.version(),
                schema.questions(), stateMapper::map, ALevelJudgmentSchemaV2::nameOf, model, costs, trace);
    }

    @Override public AssessmentJudgmentSet judge(ALevelJudgmentInput input) { return adapter.judge(input); }
    @Override public AssessmentSource source() { return AssessmentSource.OPENAI; }
    @Override public Optional<String> modelIdentifier() { return Optional.of(model); }
}
