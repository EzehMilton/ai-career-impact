package com.chikere.futureai.assessment.university.persistence;

import com.chikere.futureai.assessment.domain.AssessmentDecision;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionFactor;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV2;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.decision.university.UniversityMeasureResult;
import com.chikere.futureai.decision.university.UniversityProfileDecisionResult;
import com.chikere.futureai.decision.university.UniversityProfileFactor;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.decision.university.UniversityProfileMeasureResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.function.Function;
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
        var headline = result.headline();
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
        if (result.usesCourseProfile()) {
            for (var measure : result.profile().measures().values()) {
                entity.profileMeasures.add(profileMeasureRow(measure));
                addProfileFactors(entity.profileFactors, measure);
            }
            return entity;
        }
        var decision = result.decision();
        entity.measures = decision.measures().values().stream().map(this::measureRow).toList();
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
        // The stored policy version decides how the result is read; a v1 assessment is never read as v2.
        UniversityAssessmentResult result;
        if (UniversityDecisionPolicyV2.POLICY_VERSION.equals(entity.decisionPolicyVersion)) {
            var profile = new EnumMap<UniversityProfileMeasure, UniversityProfileMeasureResult>(
                    UniversityProfileMeasure.class);
            entity.profileMeasures.forEach(row -> profile.put(row.measure, profileMeasure(entity, row)));
            result = new UniversityAssessmentResult(new UniversityProfileDecisionResult(entity.decisionPolicyVersion,
                    entity.informationSufficiency, headline, profile), entity.source, entity.judgmentSchemaVersion,
                    entity.decisionPolicyVersion);
        }
        else {
            var measures = new EnumMap<UniversityMeasure, UniversityMeasureResult>(UniversityMeasure.class);
            entity.measures.stream().map(this::measure).forEach(measure -> measures.put(measure.measure(), measure));
            var decision = new UniversityDecisionResult(entity.decisionPolicyVersion, entity.informationSufficiency,
                    headline, measures, factors(entity, STRENGTH), factors(entity, DEVELOPMENT));
            result = new UniversityAssessmentResult(decision, entity.source,
                    entity.judgmentSchemaVersion, entity.decisionPolicyVersion);
        }
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

    private UniversityAssessmentEntity.ProfileMeasureRow profileMeasureRow(UniversityProfileMeasureResult measure) {
        var row = new UniversityAssessmentEntity.ProfileMeasureRow();
        row.measure = measure.measure();
        row.indicator = measure.indicator();
        row.band = measure.band();
        row.confidence = measure.confidence();
        row.lowerConfidence = measure.lowerConfidence();
        return row;
    }

    private void addProfileFactors(List<UniversityAssessmentEntity.ProfileFactorRow> rows,
            UniversityProfileMeasureResult measure) {
        for (int index = 0; index < measure.contributions().size(); index++) {
            var factor = measure.contributions().get(index);
            var row = new UniversityAssessmentEntity.ProfileFactorRow();
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

    private UniversityProfileMeasureResult profileMeasure(UniversityAssessmentEntity entity,
            UniversityAssessmentEntity.ProfileMeasureRow row) {
        List<UniversityAssessmentEntity.ProfileFactorRow> factors = entity.profileFactors.stream()
                .filter(factor -> factor.measure == row.measure)
                .sorted(Comparator.comparingInt(factor -> factor.position)).toList();
        Function<UniversityAssessmentEntity.ProfileFactorRow, UniversityProfileFactor> toFactor = factor ->
                new UniversityProfileFactor(factor.dimensionId, factor.score, factor.weight, factor.effect);
        return new UniversityProfileMeasureResult(row.measure, row.indicator, row.band, row.confidence,
                row.lowerConfidence, factors.stream().map(toFactor).toList(),
                factors.stream().filter(factor -> factor.driverPosition != null)
                        .sorted(Comparator.comparingInt(factor -> factor.driverPosition)).map(toFactor).toList(),
                factors.stream().filter(factor -> factor.limitingPosition != null)
                        .sorted(Comparator.comparingInt(factor -> factor.limitingPosition)).map(toFactor).toList());
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
