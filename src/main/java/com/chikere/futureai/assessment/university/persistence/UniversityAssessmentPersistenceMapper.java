package com.chikere.futureai.assessment.university.persistence;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionFactor;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.decision.university.UniversityMeasureResult;
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
final class UniversityAssessmentPersistenceMapper {

    private static final String STRENGTH = "STRENGTH";
    private static final String DEVELOPMENT = "DEVELOPMENT";

    UniversityAssessmentEntity toEntity(StoredUniversityAssessment snapshot) {
        var entity = new UniversityAssessmentEntity();
        var state = snapshot.state();
        var result = snapshot.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        entity.id = snapshot.id();
        entity.createdAt = snapshot.createdAt();
        entity.courseTitle = state.courseTitle();
        entity.courseDescription = state.courseDescription();
        entity.keyModules = state.keyModules();
        entity.skillsDeveloped = state.skillsDeveloped();
        entity.preferredCareerAreas = state.preferredCareerAreas();
        entity.institution = state.institution().orElse(null);
        entity.country = state.country().orElse(null);
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

    StoredUniversityAssessment toSnapshot(UniversityAssessmentEntity entity) {
        var state = new UniversityAssessmentState(entity.courseTitle, entity.courseDescription,
                entity.keyModules, entity.skillsDeveloped, entity.preferredCareerAreas,
                Optional.ofNullable(entity.institution), Optional.ofNullable(entity.country));
        var judgments = new AssessmentJudgmentSet(entity.judgmentJourney, entity.judgmentSchemaVersion,
                entity.judgments.stream().map(this::judgment).toList());
        var headline = new AssessmentDecision(entity.decisionJourney,
                entity.overallIndicator == null ? OptionalDouble.empty() : OptionalDouble.of(entity.overallIndicator),
                entity.resultLabel, entity.assessmentConfidence, entity.assessmentStatus,
                entity.keyStrengths, entity.developmentOpportunities);
        var measures = new EnumMap<UniversityMeasure, UniversityMeasureResult>(UniversityMeasure.class);
        entity.measures.stream().map(this::measure).forEach(measure -> measures.put(measure.measure(), measure));
        var decision = new UniversityDecisionResult(entity.decisionPolicyVersion, entity.informationSufficiency,
                headline, measures, factors(entity, STRENGTH), factors(entity, DEVELOPMENT));
        var result = new UniversityAssessmentResult(decision, entity.source,
                entity.judgmentSchemaVersion, entity.decisionPolicyVersion);
        return new StoredUniversityAssessment(entity.id, entity.createdAt, state, judgments, result,
                Optional.ofNullable(entity.modelIdentifier));
    }

    private UniversityAssessmentEntity.JudgmentRow judgmentRow(DimensionJudgment judgment) {
        var row = new UniversityAssessmentEntity.JudgmentRow();
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

    private DimensionJudgment judgment(UniversityAssessmentEntity.JudgmentRow row) {
        return new DimensionJudgment(row.dimensionId, row.dimensionName,
                row.numericValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.numericValue),
                Optional.ofNullable(row.categoricalValue), Optional.ofNullable(row.confidence),
                row.confidenceValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.confidenceValue),
                row.probabilityDistribution);
    }

    private UniversityAssessmentEntity.MeasureRow measureRow(UniversityMeasureResult measure) {
        var row = new UniversityAssessmentEntity.MeasureRow();
        row.measure = measure.measure();
        row.indicator = measure.indicator();
        row.band = measure.band();
        row.confidence = measure.confidence();
        return row;
    }

    private UniversityMeasureResult measure(UniversityAssessmentEntity.MeasureRow row) {
        return new UniversityMeasureResult(row.measure, row.indicator, row.band, row.confidence);
    }

    private void addFactors(List<UniversityAssessmentEntity.FactorRow> rows, String type,
            List<UniversityDecisionFactor> factors) {
        for (int index = 0; index < factors.size(); index++) {
            var factor = factors.get(index);
            var row = new UniversityAssessmentEntity.FactorRow();
            row.factorType = type;
            row.position = index;
            row.dimensionId = factor.dimensionId();
            row.effect = factor.effect();
            row.direction = factor.direction();
            row.score = factor.score();
            row.readinessComponent = factor.readinessComponent();
            row.weight = factor.weight();
            row.signedEffect = factor.signedEffect();
            rows.add(row);
        }
    }

    private List<UniversityDecisionFactor> factors(UniversityAssessmentEntity entity, String type) {
        return entity.factors.stream()
                .filter(row -> row.factorType.equals(type))
                .sorted(Comparator.comparingInt(row -> row.position))
                .map(row -> new UniversityDecisionFactor(row.dimensionId, row.effect, row.direction,
                        row.score, row.readinessComponent, row.weight, row.signedEffect))
                .toList();
    }
}
