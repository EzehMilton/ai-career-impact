package com.chikere.futureai.judgment.openai.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.openai.OpenAiAssessmentJudgmentAdapter;
import com.chikere.futureai.judgment.typesafe.university.UniversityDimensionNames;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.ai.decision.DecisionModel;

import java.util.Optional;

public final class UniversityOpenAiJudgmentService implements UniversityJudgmentProvider {
    private final OpenAiAssessmentJudgmentAdapter<UniversityAssessmentState> adapter;
    private final String model;

    public UniversityOpenAiJudgmentService(DecisionModel decisionModel, UniversityJudgmentSchema schema,
            UniversityJevStateMapper stateMapper, String model, ProviderCostTracker costs, AssessmentTrace trace) {
        this.model = model;
        this.adapter = new OpenAiAssessmentJudgmentAdapter<>(decisionModel, JourneyType.UNIVERSITY, schema.version(),
                schema.questions(), stateMapper::map, UniversityDimensionNames::of, model, costs, trace);
    }

    @Override public AssessmentJudgmentSet judge(UniversityAssessmentState state) { return adapter.judge(state); }
    @Override public AssessmentSource source() { return AssessmentSource.OPENAI; }
    @Override public Optional<String> modelIdentifier() { return Optional.of(model); }
}
