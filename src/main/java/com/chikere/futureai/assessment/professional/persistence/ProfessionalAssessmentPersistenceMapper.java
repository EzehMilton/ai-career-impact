package com.chikere.futureai.assessment.professional.persistence;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.decision.professional.ProfessionalMeasureResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@Component
final class ProfessionalAssessmentPersistenceMapper {

    ProfessionalAssessmentEntity toEntity(StoredProfessionalAssessment snapshot) {
        var entity = new ProfessionalAssessmentEntity();
        var state = snapshot.state();
        var result = snapshot.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        entity.id = snapshot.id();
        entity.createdAt = snapshot.createdAt();
        entity.profession = state.profession();
        entity.workDescription = state.workDescription();
        entity.responsibilities = state.responsibilities();
        entity.skills = state.skills();
        entity.yearsOfExperience = state.yearsOfExperience().isPresent()
                ? state.yearsOfExperience().getAsInt() : null;
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
        entity.keyDrivers = headline.keyDrivers();
        entity.decisionProtectiveFactors = headline.protectiveFactors();
        entity.judgments = snapshot.judgments().judgments().stream().map(this::judgmentRow).toList();
        entity.measures = decision.measures().values().stream().map(this::measureRow).toList();
        entity.factors = new ArrayList<>();
        addFactors(entity.factors, "DRIVER", decision.topExposureDrivers());
        addFactors(entity.factors, "PROTECTIVE", decision.topProtectiveFactors());
        return entity;
    }

    StoredProfessionalAssessment toSnapshot(ProfessionalAssessmentEntity entity) {
        var state = new ProfessionalAssessmentState(entity.profession, entity.workDescription,
                entity.responsibilities, entity.skills,
                entity.yearsOfExperience == null ? OptionalInt.empty() : OptionalInt.of(entity.yearsOfExperience));
        var judgments = new AssessmentJudgmentSet(entity.judgmentJourney, entity.judgmentSchemaVersion,
                entity.judgments.stream().map(this::judgment).toList());
        var headline = new AssessmentDecision(entity.decisionJourney,
                entity.overallIndicator == null ? OptionalDouble.empty() : OptionalDouble.of(entity.overallIndicator),
                entity.resultLabel, entity.assessmentConfidence, entity.assessmentStatus,
                entity.keyDrivers, entity.decisionProtectiveFactors);
        var measures = new EnumMap<com.chikere.futureai.decision.professional.ProfessionalMeasure,
                ProfessionalMeasureResult>(com.chikere.futureai.decision.professional.ProfessionalMeasure.class);
        entity.measures.stream().map(this::measure).forEach(measure -> measures.put(measure.measure(), measure));
        var decision = new ProfessionalDecisionResult(entity.decisionPolicyVersion,
                entity.informationSufficiency, headline, measures,
                factors(entity, "DRIVER"), factors(entity, "PROTECTIVE"));
        var result = new ProfessionalAssessmentResult(decision, entity.source,
                entity.judgmentSchemaVersion, entity.decisionPolicyVersion);
        return new StoredProfessionalAssessment(entity.id, entity.createdAt, state, judgments,
                result, Optional.ofNullable(entity.modelIdentifier));
    }

    private ProfessionalAssessmentEntity.JudgmentRow judgmentRow(DimensionJudgment judgment) {
        var row = new ProfessionalAssessmentEntity.JudgmentRow();
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

    private DimensionJudgment judgment(ProfessionalAssessmentEntity.JudgmentRow row) {
        return new DimensionJudgment(row.dimensionId, row.dimensionName,
                row.numericValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.numericValue),
                Optional.ofNullable(row.categoricalValue), Optional.ofNullable(row.confidence),
                row.confidenceValue == null ? OptionalDouble.empty() : OptionalDouble.of(row.confidenceValue),
                row.probabilityDistribution);
    }

    private ProfessionalAssessmentEntity.MeasureRow measureRow(ProfessionalMeasureResult measure) {
        var row = new ProfessionalAssessmentEntity.MeasureRow();
        row.measure = measure.measure();
        row.indicator = measure.indicator();
        row.band = measure.band();
        row.confidence = measure.confidence();
        return row;
    }

    private ProfessionalMeasureResult measure(ProfessionalAssessmentEntity.MeasureRow row) {
        return new ProfessionalMeasureResult(row.measure, row.indicator, row.band, row.confidence);
    }

    private void addFactors(List<ProfessionalAssessmentEntity.FactorRow> rows,
                            String type, List<ProfessionalDecisionFactor> factors) {
        for (int index = 0; index < factors.size(); index++) {
            var factor = factors.get(index);
            var row = new ProfessionalAssessmentEntity.FactorRow();
            row.factorType = type;
            row.position = index;
            row.dimensionId = factor.dimensionId();
            row.effect = factor.effect();
            row.direction = factor.direction();
            row.score = factor.score();
            row.exposureComponent = factor.exposureComponent();
            row.weight = factor.weight();
            row.signedEffect = factor.signedEffect();
            rows.add(row);
        }
    }

    private List<ProfessionalDecisionFactor> factors(ProfessionalAssessmentEntity entity, String type) {
        return entity.factors.stream()
                .filter(row -> row.factorType.equals(type))
                .sorted(java.util.Comparator.comparingInt(row -> row.position))
                .map(row -> new ProfessionalDecisionFactor(row.dimensionId, row.effect, row.direction,
                        row.score, row.exposureComponent, row.weight, row.signedEffect))
                .toList();
    }
}
