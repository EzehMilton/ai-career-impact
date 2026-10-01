package com.chikere.futureai.assessment.alevel.persistence;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.decision.alevel.ALevelDecisionFactor;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.ALevelMeasureResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Component
final class ALevelAssessmentPersistenceMapper {

    private static final String STRENGTH = "STRENGTH";
    private static final String DEVELOPMENT = "DEVELOPMENT";

    ALevelAssessmentEntity toEntity(StoredALevelAssessment snapshot) {
        var entity = new ALevelAssessmentEntity();
        var state = snapshot.state();
        var result = snapshot.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        entity.id = snapshot.id();
        entity.createdAt = snapshot.createdAt();
        entity.selectedSubjects = state.selectedSubjects();
        entity.predictedGrades = state.predictedGrades();
        entity.strengthsAndInterests = state.strengthsAndInterests();
        entity.preferredDegreeAreas = state.preferredDegreeAreas();
        entity.preferredCareerAreas = state.preferredCareerAreas();
        entity.country = state.country().orElse(null);
        entity.additionalContext = state.additionalContext().orElse(null);
        entity.source = result.source();
        entity.modelIdentifier = snapshot.modelIdentifier().orElse(null);
        entity.judgmentSchemaVersion = result.judgmentSchemaVersion();
        entity.decisionPolicyVersion = result.decisionPolicyVersion();
        entity.judgmentJourney = snapshot.judgments().journey();
        entity.informationSufficiency = decision.informationSufficiency();
        entity.decisionJourney = headline.journey();
        entity.overallIndicator = headline.overallIndicator().isPresent()
                ? headline.overallIndicator().getAsDouble() : null;
        entity.resultLabel = headline.resultLabel();
        entity.assessmentConfidence = headline.confidence();
        entity.assessmentStatus = headline.status();
        entity.keyStrengths = headline.keyDrivers();
        entity.developmentOpportunities = headline.protectiveFactors();
        entity.judgments = snapshot.judgments().judgments().stream().map(this::judgmentRow).toList();
        entity.measures = decision.measures().values().stream().map(this::measureRow).toList();
        entity.factors = new ArrayList<>();
        addFactors(entity.factors, STRENGTH, decision.topStrengths());
        addFactors(entity.factors, DEVELOPMENT, decision.topDevelopmentOpportunities());
        return entity;
    }

    StoredALevelAssessment toSnapshot(ALevelAssessmentEntity entity) {
        var state = new ALevelAssessmentState(entity.selectedSubjects, entity.predictedGrades,
                entity.strengthsAndInterests, entity.preferredDegreeAreas, entity.preferredCareerAreas,
                Optional.ofNullable(entity.country), Optional.ofNullable(entity.additionalContext));
        var judgments = new AssessmentJudgmentSet(entity.judgmentJourney, entity.judgmentSchemaVersion,
                entity.judgments.stream().map(this::judgment).toList());
        var headline = new AssessmentDecision(entity.decisionJourney,
                entity.overallIndicator == null ? OptionalDouble.empty() : OptionalDouble.of(entity.overallIndicator),
                entity.resultLabel, entity.assessmentConfidence, entity.assessmentStatus,
                entity.keyStrengths, entity.developmentOpportunities);
        var measures = new EnumMap<ALevelMeasure, ALevelMeasureResult>(ALevelMeasure.class);
        entity.measures.stream().map(this::measure).forEach(measure -> measures.put(measure.measure(), measure));
        var decision = new ALevelDecisionResult(entity.decisionPolicyVersion, entity.informationSufficiency,
                headline, measures, factors(entity, STRENGTH), factors(entity, DEVELOPMENT));
        var result = new ALevelAssessmentResult(decision, entity.source,
                entity.judgmentSchemaVersion, entity.decisionPolicyVersion);
        return new StoredALevelAssessment(entity.id, entity.createdAt, state, judgments, result,
                Optional.ofNullable(entity.modelIdentifier));
    }

    private ALevelAssessmentEntity.JudgmentRow judgmentRow(DimensionJudgment judgment) {
        var row = new ALevelAssessmentEntity.JudgmentRow();
        row.dimensionId = judgment.dimensionId();
        row.dimensionName = judgment.name();
        row.numericValue = judgment.numericValue().isPresent() ? judgment.numericValue().getAsDouble() : null;
        row.categoricalValue = judgment.categoricalValue().orElse(null);
        row.confidence = judgment.confidence().orElse(null);
        row.confidenceValue = judgment.confidenceValue().isPresent()
                ? judgment.confidenceValue().getAsDouble() : null;
        row.probabilityDistribution = judgment.probabilityDistribution();
        return row;
    }

    private DimensionJudgment judgment(ALevelAssessmentEntity.JudgmentRow row) {
        return new DimensionJudgment(row.dimensionId, row.dimensionName,
                row.numericValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.numericValue),
                Optional.ofNullable(row.categoricalValue), Optional.ofNullable(row.confidence),
                row.confidenceValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.confidenceValue),
                row.probabilityDistribution);
    }

    private ALevelAssessmentEntity.MeasureRow measureRow(ALevelMeasureResult measure) {
        var row = new ALevelAssessmentEntity.MeasureRow();
        row.measure = measure.measure();
        row.indicator = measure.indicator();
        row.band = measure.band();
        row.confidence = measure.confidence();
        return row;
    }

    private ALevelMeasureResult measure(ALevelAssessmentEntity.MeasureRow row) {
        return new ALevelMeasureResult(row.measure, row.indicator, row.band, row.confidence);
    }

    private void addFactors(List<ALevelAssessmentEntity.FactorRow> rows, String type,
            List<ALevelDecisionFactor> factors) {
        for (int index = 0; index < factors.size(); index++) {
            var factor = factors.get(index);
            var row = new ALevelAssessmentEntity.FactorRow();
            row.factorType = type;
            row.position = index;
            row.dimensionId = factor.dimensionId();
            row.effect = factor.effect();
            row.direction = factor.direction();
            row.score = factor.score();
            row.optionalityComponent = factor.optionalityComponent();
            row.weight = factor.weight();
            row.signedEffect = factor.signedEffect();
            rows.add(row);
        }
    }

    private List<ALevelDecisionFactor> factors(ALevelAssessmentEntity entity, String type) {
        return entity.factors.stream()
                .filter(row -> row.factorType.equals(type))
                .sorted(Comparator.comparingInt(row -> row.position))
                .map(row -> new ALevelDecisionFactor(row.dimensionId, row.effect, row.direction,
                        row.score, row.optionalityComponent, row.weight, row.signedEffect))
                .toList();
    }
}
