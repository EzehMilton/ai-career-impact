package com.chikere.futureai.assessment.alevel.persistence;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.decision.alevel.ALevelDecisionFactor;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileFactor;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasureResult;
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
import java.util.function.Function;

@Component
final class ALevelAssessmentPersistenceMapper {

    private static final String STRENGTH = "STRENGTH";
    private static final String DEVELOPMENT = "DEVELOPMENT";

    ALevelAssessmentEntity toEntity(StoredALevelAssessment snapshot) {
        var entity = new ALevelAssessmentEntity();
        var state = snapshot.state();
        var result = snapshot.result();
        var headline = result.headline();
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
        entity.informationSufficiency = result.informationSufficiency();
        entity.decisionJourney = headline.journey();
        entity.overallIndicator = headline.overallIndicator().isPresent()
                ? headline.overallIndicator().getAsDouble() : null;
        entity.resultLabel = headline.resultLabel();
        entity.assessmentConfidence = headline.confidence();
        entity.assessmentStatus = headline.status();
        entity.keyStrengths = headline.keyDrivers();
        entity.developmentOpportunities = headline.protectiveFactors();
        entity.judgments = snapshot.judgments().judgments().stream().map(this::judgmentRow).toList();
        entity.factors = new ArrayList<>();
        if (result.usesSubjectProfile()) {
            // A v2 subject profile: its own rows only, never the v1 measure and factor tables.
            entity.measures = new ArrayList<>();
            entity.profileMeasures = new ArrayList<>();
            entity.profileFactors = new ArrayList<>();
            for (var measure : result.profile().measures().values()) {
                entity.profileMeasures.add(profileMeasureRow(measure));
                addProfileFactors(entity.profileFactors, measure);
            }
        }
        else {
            var decision = result.decision();
            entity.measures = decision.measures().values().stream().map(this::measureRow).toList();
            addFactors(entity.factors, STRENGTH, decision.topStrengths());
            addFactors(entity.factors, DEVELOPMENT, decision.topDevelopmentOpportunities());
        }
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
        ALevelAssessmentResult result;
        // Routed by the stored policy version, never by the running application's policy.
        if (ALevelDecisionPolicyV2.POLICY_VERSION.equals(entity.decisionPolicyVersion)) {
            var profile = new EnumMap<ALevelProfileMeasure, ALevelProfileMeasureResult>(ALevelProfileMeasure.class);
            entity.profileMeasures.forEach(row -> profile.put(row.measure, profileMeasure(entity, row)));
            result = new ALevelAssessmentResult(new ALevelProfileDecisionResult(entity.decisionPolicyVersion,
                    entity.informationSufficiency, headline, profile), entity.source, entity.judgmentSchemaVersion,
                    entity.decisionPolicyVersion);
        }
        else {
            var measures = new EnumMap<ALevelMeasure, ALevelMeasureResult>(ALevelMeasure.class);
            entity.measures.stream().map(this::measure).forEach(measure -> measures.put(measure.measure(), measure));
            var decision = new ALevelDecisionResult(entity.decisionPolicyVersion, entity.informationSufficiency,
                    headline, measures, factors(entity, STRENGTH), factors(entity, DEVELOPMENT));
            result = new ALevelAssessmentResult(decision, entity.source, entity.judgmentSchemaVersion,
                    entity.decisionPolicyVersion);
        }
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

    private ALevelAssessmentEntity.ProfileMeasureRow profileMeasureRow(ALevelProfileMeasureResult measure) {
        var row = new ALevelAssessmentEntity.ProfileMeasureRow();
        row.measure = measure.measure();
        row.indicator = measure.indicator();
        row.band = measure.band();
        row.confidence = measure.confidence();
        row.lowerConfidence = measure.lowerConfidence();
        return row;
    }

    private void addProfileFactors(List<ALevelAssessmentEntity.ProfileFactorRow> rows,
            ALevelProfileMeasureResult measure) {
        for (int index = 0; index < measure.contributions().size(); index++) {
            var factor = measure.contributions().get(index);
            var row = new ALevelAssessmentEntity.ProfileFactorRow();
            row.measure = measure.measure();
            row.position = index;
            row.dimensionId = factor.dimensionId();
            row.score = factor.score();
            row.weight = factor.weight();
            row.effect = factor.effect();
            int driver = measure.drivers().indexOf(factor);
            int limiting = measure.limitingFactors().indexOf(factor);
            row.driverPosition = driver < 0 ? null : driver;
            row.limitingPosition = limiting < 0 ? null : limiting;
            rows.add(row);
        }
    }

    private ALevelProfileMeasureResult profileMeasure(ALevelAssessmentEntity entity,
            ALevelAssessmentEntity.ProfileMeasureRow row) {
        List<ALevelAssessmentEntity.ProfileFactorRow> factors = entity.profileFactors.stream()
                .filter(factor -> factor.measure == row.measure)
                .sorted(Comparator.comparingInt(factor -> factor.position)).toList();
        Function<ALevelAssessmentEntity.ProfileFactorRow, ALevelProfileFactor> toFactor = factor ->
                new ALevelProfileFactor(factor.dimensionId, factor.score, factor.weight, factor.effect);
        return new ALevelProfileMeasureResult(row.measure, row.indicator, row.band, row.confidence,
                row.lowerConfidence, factors.stream().map(toFactor).toList(),
                factors.stream().filter(factor -> factor.driverPosition != null)
                        .sorted(Comparator.comparingInt(factor -> factor.driverPosition)).map(toFactor).toList(),
                factors.stream().filter(factor -> factor.limitingPosition != null)
                        .sorted(Comparator.comparingInt(factor -> factor.limitingPosition)).map(toFactor).toList());
    }
}
